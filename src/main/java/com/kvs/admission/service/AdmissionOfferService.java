package com.kvs.admission.service;

import com.kvs.admission.dto.AdmissionOfferResponse;
import com.kvs.admission.dto.IssueOfferRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmissionOffer;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.OfferStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmissionOfferRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for generating and dispatching admission offer letters.
 * Covers user story:
 * US-02006: Issue automated offer letters with embedded online fee payment links
 * Actor: Finance Cashier
 */
@Service
@Slf4j
public class AdmissionOfferService {

    private static final Set<String> OFFERABLE_STATUSES = Set.of(
            EnquiryStatus.EVALUATION_PASSED.name(),
            EnquiryStatus.OFFER_ISSUED.name());

    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    @Value("${admission.payment.link-base-url:https://pay.kvs.local/admissions/offers}")
    private String paymentLinkBaseUrl;

    /**
     * Generates an offer letter for an evaluated candidate, embeds a unique online payment link
     * for the initial fee, and marks the offer DISPATCHED. Only one live offer may exist per enquiry.
     *
     * @param request the offer request
     * @return the dispatched offer
     */
    @Transactional
    public AdmissionOfferResponse issueOffer(IssueOfferRequest request) {
        log.info("Issuing admission offer for enquiry: {}", request.getEnquiryId());
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(request.getEnquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", request.getEnquiryId()));

        if (!OFFERABLE_STATUSES.contains(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("An offer can only be issued after the candidate passes evaluation. "
                    + "Enquiry " + enquiry.getApplicationNumber() + " is " + enquiry.getEnquiryStatus());
        }

        for (AdmissionOffer existing : offerRepository.findByEnquiryIdOrderByCreatedAtDesc(enquiry.getEnquiryId())) {
            if (existing.getOfferStatus() == OfferStatus.DISPATCHED && isPastValidity(existing)) {
                existing.setOfferStatus(OfferStatus.EXPIRED);
                offerRepository.save(existing);
            } else if (existing.getOfferStatus() == OfferStatus.DISPATCHED
                    || existing.getOfferStatus() == OfferStatus.ACCEPTED) {
                throw new ConflictException("Enquiry " + enquiry.getApplicationNumber()
                        + " already has an active offer " + existing.getOfferCode());
            }
        }

        String offerCode = ReferenceNumberGenerator.next("OFFER");
        String token = newPaymentToken();
        String gradeId = StringUtils.hasText(request.getOfferedGradeId())
                ? request.getOfferedGradeId()
                : enquiry.getTargetGradeId();
        String paymentLink = UriComponentsBuilder.fromUriString(paymentLinkBaseUrl)
                .queryParam("offer", offerCode)
                .queryParam("token", token)
                .build()
                .toUriString();

        AdmissionOffer offer = new AdmissionOffer();
        offer.setTenantId(enquiry.getTenantId());
        offer.setEnquiryId(enquiry.getEnquiryId());
        offer.setOfferCode(offerCode);
        offer.setOfferedGradeId(gradeId);
        offer.setAcademicYearId(enquiry.getAcademicYearId());
        offer.setValidUntilDate(request.getValidUntilDate());
        offer.setInitialFeeAmount(request.getInitialFeeAmount());
        offer.setCurrency("INR");
        offer.setPaymentLinkToken(token);
        offer.setPaymentLinkUrl(paymentLink);
        offer.setLetterContent(renderLetter(enquiry, offerCode, gradeId, request.getInitialFeeAmount(),
                request.getValidUntilDate(), paymentLink));
        offer.setOfferStatus(OfferStatus.DISPATCHED);
        offer.setDispatchedAt(Instant.now());
        AdmissionOffer saved = offerRepository.save(offer);

        enquiry.setEnquiryStatus(EnquiryStatus.OFFER_ISSUED.name());
        admissionEnquiryRepository.save(enquiry);

        log.info("Dispatched offer {} for application {}", offerCode, enquiry.getApplicationNumber());
        auditService.logSuccess("admission_offer:" + enquiry.getTenantId(), "ISSUE_ADMISSION_OFFER",
                "Issued offer " + offerCode + " for application " + enquiry.getApplicationNumber()
                        + " | Grade: " + gradeId
                        + " | Fee: INR " + request.getInitialFeeAmount()
                        + " | Valid until: " + request.getValidUntilDate());

        return mapToResponse(saved, enquiry);
    }

    /**
     * Retrieves an offer. A dispatched offer past its validity date is reported as EXPIRED.
     *
     * @param offerId the offer UUID
     * @return the offer
     */
    @Transactional(readOnly = true)
    public AdmissionOfferResponse getOffer(UUID offerId) {
        log.info("Fetching admission offer: {}", offerId);
        AdmissionOffer offer = offerRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionOffer", "offerId", offerId));
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(offer.getEnquiryId()).orElse(null);
        return mapToResponse(offer, enquiry);
    }

    /**
     * Lists all offers issued for an enquiry, newest first.
     *
     * @param enquiryId the enquiry UUID
     * @return offers
     */
    @Transactional(readOnly = true)
    public List<AdmissionOfferResponse> listOffers(UUID enquiryId) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        return offerRepository.findByEnquiryIdOrderByCreatedAtDesc(enquiryId).stream()
                .map(o -> mapToResponse(o, enquiry))
                .toList();
    }

    static boolean isPastValidity(AdmissionOffer offer) {
        return offer.getValidUntilDate().isBefore(LocalDate.now());
    }

    private String newPaymentToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String renderLetter(AdmissionEnquiry enquiry, String offerCode, String gradeId, BigDecimal fee,
                                LocalDate validUntil, String paymentLink) {
        return "Offer Reference: " + offerCode + "\n"
                + "Date: " + LocalDate.now().format(LETTER_DATE) + "\n\n"
                + "Dear " + enquiry.getGuardianName() + ",\n\n"
                + "We are pleased to offer admission to " + enquiry.getCandidateFirstName() + " "
                + enquiry.getCandidateLastName() + " (Application " + enquiry.getApplicationNumber() + ") "
                + "for grade " + gradeId + " in academic year " + enquiry.getAcademicYearId() + ".\n\n"
                + "To confirm the seat, please pay the seat reservation fee of INR " + fee.toPlainString()
                + " on or before " + validUntil.format(LETTER_DATE) + " using the secure link below:\n"
                + paymentLink + "\n\n"
                + "The offer lapses automatically if the fee is not received by the due date.\n\n"
                + "Admissions Office";
    }

    private AdmissionOfferResponse mapToResponse(AdmissionOffer offer, AdmissionEnquiry enquiry) {
        OfferStatus status = offer.getOfferStatus() == OfferStatus.DISPATCHED && isPastValidity(offer)
                ? OfferStatus.EXPIRED
                : offer.getOfferStatus();
        return AdmissionOfferResponse.builder()
                .offerId(offer.getOfferId())
                .tenantId(offer.getTenantId())
                .enquiryId(offer.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .candidateName(enquiry != null
                        ? enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName()
                        : null)
                .guardianName(enquiry != null ? enquiry.getGuardianName() : null)
                .guardianEmail(enquiry != null ? enquiry.getGuardianEmail() : null)
                .offerCode(offer.getOfferCode())
                .offeredGradeId(offer.getOfferedGradeId())
                .academicYearId(offer.getAcademicYearId())
                .offerStatus(status.name())
                .validUntilDate(offer.getValidUntilDate())
                .initialFeeAmount(offer.getInitialFeeAmount())
                .currency(offer.getCurrency())
                .paymentLinkUrl(offer.getPaymentLinkUrl())
                .letterContent(offer.getLetterContent())
                .dispatchedAt(offer.getDispatchedAt())
                .acceptedAt(offer.getAcceptedAt())
                .createdAt(offer.getCreatedAt())
                .build();
    }
}
