package com.kvs.admission.service;

import com.kvs.admission.dto.ReminderDispatchSummaryResponse;
import com.kvs.admission.dto.ReminderNotificationResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmitCard;
import com.kvs.admission.entity.ApplicantNotification;
import com.kvs.admission.entity.EntranceExamSchedule;
import com.kvs.admission.enums.*;
import com.kvs.admission.notification.AdmissionEmailCopy;
import com.kvs.admission.notification.WhatsAppGateway;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmitCardRepository;
import com.kvs.admission.repo.ApplicantNotificationRepository;
import com.kvs.admission.repo.EntranceExamScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service dispatching WhatsApp reminders to candidates with upcoming entrance evaluations.
 * Covers user story:
 * US-02009: Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 * Actor: Front-Office Counselor
 */
@Service
@Slf4j
public class ApplicantReminderService {

    static final int DEFAULT_LOOKAHEAD_HOURS = 48;

    private static final DateTimeFormatter MESSAGE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    // email copy to the guardian from the Admissions mailbox (Settings → Integrations)
    @Autowired
    private AdmissionEmailCopy emailCopy;

    @Autowired
    private EntranceExamScheduleRepository examScheduleRepository;

    @Autowired
    private AdmitCardRepository admitCardRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private ApplicantNotificationRepository notificationRepository;

    @Autowired
    private WhatsAppGateway whatsAppGateway;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Sends a WhatsApp reminder to the guardian of every admit-card holder whose exam starts within the
     * lookahead window. Candidates already reminded for the same exam are skipped, so re-running is safe;
     * failed sends are recorded and retried on the next run.
     *
     * @param tenantId       the tenant
     * @param branchId       the branch
     * @param lookaheadHours window size in hours, defaults to 48
     * @return dispatch summary
     */
    @Transactional
    public ReminderDispatchSummaryResponse dispatchEntranceEvaluationReminders(String tenantId, String branchId,
                                                                               Integer lookaheadHours) {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofHours(lookaheadHours != null ? lookaheadHours : DEFAULT_LOOKAHEAD_HOURS));
        log.info("Dispatching entrance evaluation reminders for tenant: {}, branch: {}, window until {}",
                tenantId, branchId, windowEnd);

        List<EntranceExamSchedule> exams = examScheduleRepository
                .findByTenantIdAndBranchIdAndStatusAndExamStartAtBetweenOrderByExamStartAtAsc(
                        tenantId, branchId, ExamScheduleStatus.SCHEDULED, now, windowEnd);
        ReminderDispatchSummaryResponse summary = dispatch(exams);
        summary.setTenantId(tenantId);
        summary.setBranchId(branchId);
        summary.setWindowStart(now);
        summary.setWindowEnd(windowEnd);

        auditService.logSuccess("applicant_notification:" + tenantId, "DISPATCH_WHATSAPP_EXAM_REMINDERS",
                "Branch: " + branchId + " | Exams: " + summary.getExamsCovered()
                        + " | Sent: " + summary.getRemindersSent()
                        + " | Skipped: " + summary.getRemindersSkipped()
                        + " | Failed: " + summary.getRemindersFailed());
        return summary;
    }

    /**
     * Scheduled variant across all tenants, invoked by {@code ApplicantReminderScheduler}.
     *
     * @param lookaheadHours window size in hours
     * @return dispatch summary
     */
    @Transactional
    public ReminderDispatchSummaryResponse dispatchAllDueReminders(int lookaheadHours) {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofHours(lookaheadHours));
        List<EntranceExamSchedule> exams = examScheduleRepository
                .findByStatusAndExamStartAtBetweenOrderByExamStartAtAsc(ExamScheduleStatus.SCHEDULED, now, windowEnd);
        ReminderDispatchSummaryResponse summary = dispatch(exams);
        summary.setWindowStart(now);
        summary.setWindowEnd(windowEnd);
        if (summary.getRemindersSent() + summary.getRemindersFailed() > 0) {
            auditService.logSuccess("applicant_notification:all", "AUTO_DISPATCH_WHATSAPP_EXAM_REMINDERS",
                    "Exams: " + summary.getExamsCovered()
                            + " | Sent: " + summary.getRemindersSent()
                            + " | Skipped: " + summary.getRemindersSkipped()
                            + " | Failed: " + summary.getRemindersFailed());
        }
        return summary;
    }

    private ReminderDispatchSummaryResponse dispatch(List<EntranceExamSchedule> exams) {
        List<ReminderNotificationResponse> results = new ArrayList<>();
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        for (EntranceExamSchedule exam : exams) {
            List<AdmitCard> cards = admitCardRepository.findByExamIdAndStatusOrderByRollNumberAsc(
                    exam.getExamId(), AdmitCardStatus.ISSUED);
            if (cards.isEmpty()) {
                continue;
            }
            Set<UUID> alreadyNotified = new HashSet<>(notificationRepository.findNotifiedAdmitCards(
                    cards.stream().map(AdmitCard::getAdmitCardId).toList(),
                    NotificationType.ENTRANCE_EVALUATION_REMINDER, NotificationChannel.WHATSAPP, NotificationStatus.SENT));
            Map<UUID, AdmissionEnquiry> enquiries = admissionEnquiryRepository.findByEnquiryIdIn(
                            cards.stream().map(AdmitCard::getEnquiryId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));

            for (AdmitCard card : cards) {
                AdmissionEnquiry enquiry = enquiries.get(card.getEnquiryId());
                if (alreadyNotified.contains(card.getAdmitCardId()) || enquiry == null || isClosed(enquiry)) {
                    skipped++;
                    continue;
                }
                ApplicantNotification notification = send(exam, card, enquiry);
                ApplicantNotification copy = emailCopy.send(enquiry, notification, exam.getExamTitle() + " reminder – application " + enquiry.getApplicationNumber());
                if (notification.getStatus() == NotificationStatus.SENT) {
                    sent++;
                } else {
                    failed++;
                }
                ReminderNotificationResponse row = mapToResponse(notification, exam, card, enquiry);
                if (copy != null) {
                    row.setEmailStatus(copy.getStatus().name());
                    row.setEmailDetail(copy.getStatus() == NotificationStatus.SENT ? copy.getProviderMessageId() : copy.getFailureReason());
                }
                results.add(row);
            }
        }

        log.info("Reminder dispatch complete: exams={}, sent={}, skipped={}, failed={}", exams.size(), sent, skipped, failed);
        return ReminderDispatchSummaryResponse.builder()
                .examsCovered(exams.size())
                .remindersSent(sent)
                .remindersSkipped(skipped)
                .remindersFailed(failed)
                .notifications(results)
                .build();
    }

    private ApplicantNotification send(EntranceExamSchedule exam, AdmitCard card, AdmissionEnquiry enquiry) {
        String message = buildMessage(exam, card, enquiry);
        ApplicantNotification notification = new ApplicantNotification();
        notification.setTenantId(enquiry.getTenantId());
        notification.setEnquiryId(enquiry.getEnquiryId());
        notification.setExamId(exam.getExamId());
        notification.setAdmitCardId(card.getAdmitCardId());
        notification.setChannel(NotificationChannel.WHATSAPP);
        notification.setNotificationType(NotificationType.ENTRANCE_EVALUATION_REMINDER);
        notification.setRecipientPhone(enquiry.getGuardianPhone());
        notification.setMessageBody(message);
        notification.setDispatchedAt(Instant.now());
        try {
            notification.setProviderMessageId(whatsAppGateway.send(enquiry.getGuardianPhone(), message));
            notification.setStatus(NotificationStatus.SENT);
        } catch (RuntimeException e) {
            log.warn("WhatsApp reminder failed for application {}: {}", enquiry.getApplicationNumber(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            notification.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        }
        return notificationRepository.save(notification);
    }

    static String buildMessage(EntranceExamSchedule exam, AdmitCard card, AdmissionEnquiry enquiry) {
        String message = "Dear " + enquiry.getGuardianName() + ", this is a reminder that "
                + card.getCandidateName() + " (Application " + enquiry.getApplicationNumber() + ") is scheduled for "
                + exam.getExamTitle() + " on " + MESSAGE_TIME.format(exam.getExamStartAt()) + " IST at "
                + exam.getVenue() + ". Roll No: " + card.getRollNumber() + ", Seat: " + card.getSeatNumber()
                + ". Please carry the admit card " + card.getAdmitCardNumber() + ".";
        if (exam.getReportingInstructions() != null) {
            message += " " + exam.getReportingInstructions();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private boolean isClosed(AdmissionEnquiry enquiry) {
        String status = enquiry.getEnquiryStatus();
        return EnquiryStatus.REJECTED.name().equals(status) || EnquiryStatus.WITHDRAWN.name().equals(status);
    }

    private ReminderNotificationResponse mapToResponse(ApplicantNotification n, EntranceExamSchedule exam,
                                                       AdmitCard card, AdmissionEnquiry enquiry) {
        return ReminderNotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .enquiryId(n.getEnquiryId())
                .applicationNumber(enquiry.getApplicationNumber())
                .examId(exam.getExamId())
                .examCode(exam.getExamCode())
                .rollNumber(card.getRollNumber())
                .channel(n.getChannel().name())
                .recipientPhone(n.getRecipientPhone())
                .status(n.getStatus().name())
                .providerMessageId(n.getProviderMessageId())
                .failureReason(n.getFailureReason())
                .dispatchedAt(n.getDispatchedAt())
                .build();
    }
}
