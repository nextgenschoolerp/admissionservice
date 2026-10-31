package com.kvs.admission.service;

import com.kvs.admission.dto.BulkAcceptanceRequest;
import com.kvs.admission.dto.BulkAcceptanceResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmissionOffer;
import com.kvs.admission.entity.ApplicantNotification;
import com.kvs.admission.entity.MeritList;
import com.kvs.admission.entity.MeritListEntry;
import com.kvs.admission.enums.*;
import com.kvs.admission.notification.AdmissionEmailCopy;
import com.kvs.admission.notification.WhatsAppGateway;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmissionOfferRepository;
import com.kvs.admission.repo.ApplicantNotificationRepository;
import com.kvs.admission.repo.MeritListRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service sending acceptance notifications to the pool of applicants holding a live offer.
 * Covers user stories:
 * US-02018, US-02058, US-02098: Send bulk acceptance notifications to qualified applicant pools
 * Actor: Admission Officer (Finance Cashier in US-02038/78 is not granted)
 */
@Service
@Slf4j
public class AcceptanceNotificationService {

    private static final DateTimeFormatter PAY_BY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // email copy to the guardian from the Admissions mailbox (Settings → Integrations)
    @Autowired
    private AdmissionEmailCopy emailCopy;

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private MeritListRepository meritListRepository;

    @Autowired
    private ApplicantNotificationRepository notificationRepository;

    @Autowired
    private FeeWaiverService feeWaiverService;

    @Autowired
    private WhatsAppGateway whatsAppGateway;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Sends one WhatsApp acceptance message per dispatched, unexpired offer of the grade (optionally only
     * for candidates marked qualified in a merit list). Offers already notified are skipped, so the run
     * can be repeated safely; failed sends are recorded and retried next time.
     */
    @Transactional
    public BulkAcceptanceResponse notifyPool(BulkAcceptanceRequest request) {
        Set<UUID> qualified = null;
        if (request.getMeritListId() != null) {
            MeritList list = meritListRepository.findByMeritListId(request.getMeritListId())
                    .orElseThrow(() -> new ResourceNotFoundException("MeritList", "meritListId", request.getMeritListId()));
            if (!list.getTenantId().equals(request.getTenantId()) || !list.getTargetGradeId().equals(request.getGradeId())
                    || !list.getAcademicYearId().equals(request.getAcademicYearId())) {
                throw new BadRequestException("Merit list " + list.getMeritListCode() + " belongs to a different grade or academic year");
            }
            qualified = list.getEntries().stream().filter(MeritListEntry::isQualified)
                    .map(MeritListEntry::getEnquiryId).collect(Collectors.toSet());
        }

        List<AdmissionOffer> live = offerRepository.findByTenantIdAndAcademicYearIdAndOfferedGradeIdAndOfferStatusIn(
                        request.getTenantId(), request.getAcademicYearId(), request.getGradeId(), EnumSet.of(OfferStatus.DISPATCHED))
                .stream().filter(o -> !AdmissionOfferService.isPastValidity(o)).toList();
        Map<UUID, AdmissionEnquiry> enquiries = live.isEmpty() ? Map.of()
                : admissionEnquiryRepository.findByEnquiryIdIn(live.stream().map(AdmissionOffer::getEnquiryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        Set<UUID> finalQualified = qualified;
        List<AdmissionOffer> pool = live.stream()
                .filter(o -> {
                    AdmissionEnquiry e = enquiries.get(o.getEnquiryId());
                    return e != null && request.getBranchId().equals(e.getBranchId()) && e.getArchivedAt() == null
                            && (finalQualified == null || finalQualified.contains(o.getEnquiryId()));
                })
                .sorted(Comparator.comparing(AdmissionOffer::getOfferCode))
                .toList();

        Set<UUID> alreadySent = pool.isEmpty() ? Set.of() : new HashSet<>(notificationRepository.findNotifiedOffers(
                pool.stream().map(AdmissionOffer::getOfferId).toList(), NotificationType.ACCEPTANCE_NOTIFICATION,
                NotificationChannel.WHATSAPP, NotificationStatus.SENT));

        List<BulkAcceptanceResponse.Item> items = new ArrayList<>();
        int sent = 0;
        int failed = 0;
        for (AdmissionOffer offer : pool) {
            if (alreadySent.contains(offer.getOfferId())) {
                continue;
            }
            AdmissionEnquiry enquiry = enquiries.get(offer.getEnquiryId());
            BigDecimal due = feeWaiverService.payableAmount(offer);
            ApplicantNotification n = new ApplicantNotification();
            n.setTenantId(offer.getTenantId());
            n.setEnquiryId(enquiry.getEnquiryId());
            n.setOfferId(offer.getOfferId());
            n.setChannel(NotificationChannel.WHATSAPP);
            n.setNotificationType(NotificationType.ACCEPTANCE_NOTIFICATION);
            n.setRecipientPhone(enquiry.getGuardianPhone());
            n.setMessageBody(buildMessage(enquiry, offer, due));
            n.setDispatchedAt(Instant.now());
            try {
                n.setProviderMessageId(whatsAppGateway.send(enquiry.getGuardianPhone(), n.getMessageBody()));
                n.setStatus(NotificationStatus.SENT);
                sent++;
            } catch (RuntimeException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                n.setStatus(NotificationStatus.FAILED);
                n.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
                failed++;
            }
            ApplicantNotification saved = notificationRepository.save(n);
            ApplicantNotification copy = emailCopy.send(enquiry, saved, "Admission offer " + offer.getOfferCode() + " – application " + enquiry.getApplicationNumber());
            items.add(BulkAcceptanceResponse.Item.builder()
                    .emailStatus(copy == null ? null : copy.getStatus().name())
                    .emailDetail(copy == null ? null : copy.getStatus() == NotificationStatus.SENT ? copy.getProviderMessageId() : copy.getFailureReason())
                    .notificationId(saved.getNotificationId())
                    .enquiryId(enquiry.getEnquiryId())
                    .applicationNumber(enquiry.getApplicationNumber())
                    .offerCode(offer.getOfferCode())
                    .amountDue(due)
                    .payBy(offer.getValidUntilDate())
                    .recipientPhone(saved.getRecipientPhone())
                    .status(saved.getStatus().name())
                    .providerMessageId(saved.getProviderMessageId())
                    .failureReason(saved.getFailureReason())
                    .dispatchedAt(saved.getDispatchedAt())
                    .build());
        }
        int skipped = pool.size() - items.size();
        auditService.logSuccess("applicant_notification:" + request.getTenantId(), "SEND_BULK_ACCEPTANCE_NOTIFICATIONS",
                "Grade " + request.getGradeId() + " (" + request.getAcademicYearId() + ", " + request.getBranchId() + ")"
                        + (request.getMeritListId() != null ? " | Merit list " + request.getMeritListId() : "")
                        + " | Pool: " + pool.size() + " | Sent: " + sent + " | Skipped: " + skipped + " | Failed: " + failed);
        return BulkAcceptanceResponse.builder()
                .gradeId(request.getGradeId())
                .meritListId(request.getMeritListId())
                .poolSize(pool.size())
                .sent(sent)
                .skipped(skipped)
                .failed(failed)
                .notifications(items)
                .build();
    }

    static String buildMessage(AdmissionEnquiry enquiry, AdmissionOffer offer, BigDecimal due) {
        String message = "Congratulations " + enquiry.getGuardianName() + "! " + enquiry.getCandidateFirstName() + " "
                + enquiry.getCandidateLastName() + " (Application " + enquiry.getApplicationNumber() + ") has been offered admission to "
                + offer.getOfferedGradeId() + " for " + offer.getAcademicYearId() + ". Offer " + offer.getOfferCode()
                + ": please pay INR " + due.toPlainString() + " by " + offer.getValidUntilDate().format(PAY_BY)
                + " to confirm the seat: " + offer.getPaymentLinkUrl();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
