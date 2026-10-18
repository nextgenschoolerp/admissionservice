package com.kvs.admission.service;

import com.kvs.admission.dto.FeeWaiverDecisionRequest;
import com.kvs.admission.dto.FeeWaiverResponse;
import com.kvs.admission.dto.RequestFeeWaiverRequest;
import com.kvs.admission.entity.*;
import com.kvs.admission.enums.*;
import com.kvs.admission.repo.*;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service processing scholarship waivers on the seat reservation fee.
 * Covers user stories:
 * US-02016, US-02056, US-02096: Process application fee waivers for scholarship candidates (School Principal approves)
 * The Entrance Exam Evaluator (US-02036/76) is not granted: fee concessions are a financial decision.
 */
@Service
@Slf4j
public class FeeWaiverService {

    static final BigDecimal MERIT_MIN_ENTRANCE_SCORE = new BigDecimal("85");
    private static final EnumSet<WaiverStatus> OPEN_OR_APPROVED = EnumSet.of(WaiverStatus.REQUESTED, WaiverStatus.APPROVED);

    @Autowired
    private FeeWaiverRepository waiverRepository;

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private EntranceEvaluationRepository evaluationRepository;

    @Autowired
    private AdmissionFeeDepositRepository depositRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Requests a waiver on a dispatched, still-valid offer. Each category caps the percentage, and MERIT
     * requires an entrance test score of at least 85. Only one open or approved waiver per offer.
     */
    @Transactional
    public FeeWaiverResponse requestWaiver(UUID offerId, RequestFeeWaiverRequest request) {
        AdmissionOffer offer = offerRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionOffer", "offerId", offerId));
        requireWaivableOffer(offer);
        if (waiverRepository.existsByOfferIdAndStatusIn(offerId, OPEN_OR_APPROVED)) {
            throw new ConflictException("Offer " + offer.getOfferCode() + " already has a pending or approved waiver");
        }
        BigDecimal entranceScore = evaluationRepository.findByEnquiryIdOrderByEvaluationDateAsc(offer.getEnquiryId()).stream()
                .filter(e -> e.getEvaluationType() == EvaluationType.ENTRANCE_TEST)
                .map(EntranceEvaluation::getTotalScore)
                .findFirst().orElse(null);
        String problem = validateWaiver(request.getScholarshipCategory(), request.getWaiverPercent(), entranceScore);
        if (problem != null) {
            throw new BadRequestException(problem);
        }

        BigDecimal waived = waivedAmount(offer.getInitialFeeAmount(), request.getWaiverPercent());
        FeeWaiver waiver = new FeeWaiver();
        waiver.setTenantId(offer.getTenantId());
        waiver.setOfferId(offerId);
        waiver.setEnquiryId(offer.getEnquiryId());
        waiver.setScholarshipCategory(request.getScholarshipCategory());
        waiver.setWaiverPercent(request.getWaiverPercent());
        waiver.setOriginalAmount(offer.getInitialFeeAmount());
        waiver.setWaivedAmount(waived);
        waiver.setPayableAmount(offer.getInitialFeeAmount().subtract(waived));
        waiver.setJustification(request.getJustification());
        waiver.setStatus(WaiverStatus.REQUESTED);
        waiver.setRequestedBy(SecurityUtils.getCurrentUserEmail().orElse("admission_officer"));
        FeeWaiver saved = waiverRepository.save(waiver);

        auditService.logSuccess("fee_waiver:" + offer.getTenantId(), "REQUEST_FEE_WAIVER",
                request.getScholarshipCategory() + " waiver of " + request.getWaiverPercent() + "% (INR " + waived.toPlainString()
                        + ") requested on offer " + offer.getOfferCode());
        return map(saved, offer);
    }

    /**
     * Approves or rejects a requested waiver. Only the School Principal (or Super Admin) decides, and a
     * principal cannot approve a waiver they requested. A 100% waiver accepts the offer immediately with a
     * zero-value receipt.
     */
    @Transactional
    public FeeWaiverResponse decide(UUID waiverId, FeeWaiverDecisionRequest request) {
        FeeWaiver waiver = waiverRepository.findByWaiverId(waiverId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeWaiver", "waiverId", waiverId));
        if (waiver.getStatus() != WaiverStatus.REQUESTED) {
            throw new ConflictException("Waiver " + waiverId + " is already " + waiver.getStatus());
        }
        if (request.getDecision() == WaiverStatus.REQUESTED) {
            throw new BadRequestException("Decision must be APPROVED or REJECTED");
        }
        if (request.getDecision() == WaiverStatus.REJECTED && !StringUtils.hasText(request.getDecisionNotes())) {
            throw new BadRequestException("Decision notes are required when rejecting a waiver");
        }
        String decider = SecurityUtils.getCurrentUserEmail().orElse("school_principal");
        if (decider.equalsIgnoreCase(waiver.getRequestedBy()) && !SecurityUtils.hasRole("ROLE_SUPER_ADMIN")) {
            throw new ForbiddenException("A waiver must be approved by someone other than the person who requested it");
        }
        AdmissionOffer offer = offerRepository.findByOfferIdForUpdate(waiver.getOfferId())
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionOffer", "offerId", waiver.getOfferId()));
        if (request.getDecision() == WaiverStatus.APPROVED) {
            requireWaivableOffer(offer);
        }

        waiver.setStatus(request.getDecision());
        waiver.setDecidedBy(decider);
        waiver.setDecidedAt(Instant.now());
        waiver.setDecisionNotes(request.getDecisionNotes());

        if (request.getDecision() == WaiverStatus.APPROVED && waiver.getPayableAmount().signum() == 0) {
            AdmissionFeeDeposit deposit = new AdmissionFeeDeposit();
            deposit.setTenantId(offer.getTenantId());
            deposit.setOfferId(offer.getOfferId());
            deposit.setEnquiryId(offer.getEnquiryId());
            deposit.setReceiptNumber(ReferenceNumberGenerator.next("RCPT"));
            deposit.setAmountPaid(BigDecimal.ZERO.setScale(2));
            deposit.setCurrency(offer.getCurrency());
            deposit.setPaymentMode(PaymentMode.WAIVER);
            deposit.setPaymentTimestamp(Instant.now());
            deposit.setCollectedBy(decider);
            deposit.setRemarks("Full " + waiver.getScholarshipCategory() + " scholarship waiver " + waiver.getWaiverId());
            depositRepository.save(deposit);
            offer.setOfferStatus(OfferStatus.ACCEPTED);
            offer.setAcceptedAt(deposit.getPaymentTimestamp());
            offerRepository.save(offer);
            waiver.setWaiverReceiptNumber(deposit.getReceiptNumber());
        }
        FeeWaiver saved = waiverRepository.save(waiver);

        auditService.logSuccess("fee_waiver:" + saved.getTenantId(), "DECIDE_FEE_WAIVER",
                saved.getStatus() + " " + saved.getScholarshipCategory() + " waiver " + saved.getWaiverId() + " on offer "
                        + offer.getOfferCode() + " | Payable: INR " + saved.getPayableAmount().toPlainString()
                        + (saved.getWaiverReceiptNumber() != null ? " | Receipt: " + saved.getWaiverReceiptNumber() : ""));
        return map(saved, offer);
    }

    @Transactional(readOnly = true)
    public FeeWaiverResponse getWaiver(UUID waiverId) {
        FeeWaiver waiver = waiverRepository.findByWaiverId(waiverId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeWaiver", "waiverId", waiverId));
        return map(waiver, offerRepository.findByOfferId(waiver.getOfferId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<FeeWaiverResponse> listWaivers(String tenantId, WaiverStatus status) {
        List<FeeWaiver> waivers = status != null
                ? waiverRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : waiverRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return waivers.stream().map(w -> map(w, offerRepository.findByOfferId(w.getOfferId()).orElse(null))).toList();
    }

    /**
     * Amount the family still has to pay on an offer after an approved waiver.
     */
    public BigDecimal payableAmount(AdmissionOffer offer) {
        return waiverRepository.findFirstByOfferIdAndStatus(offer.getOfferId(), WaiverStatus.APPROVED)
                .map(FeeWaiver::getPayableAmount)
                .orElse(offer.getInitialFeeAmount());
    }

    /** Returns a validation message, or null when the waiver is allowed. */
    static String validateWaiver(ScholarshipCategory category, int percent, BigDecimal entranceScore) {
        if (percent > category.getMaxWaiverPercent()) {
            return category + " waivers are capped at " + category.getMaxWaiverPercent() + "%";
        }
        if (category == ScholarshipCategory.MERIT
                && (entranceScore == null || entranceScore.compareTo(MERIT_MIN_ENTRANCE_SCORE) < 0)) {
            return "MERIT waivers require an entrance test score of at least " + MERIT_MIN_ENTRANCE_SCORE
                    + (entranceScore != null ? " (candidate scored " + entranceScore.stripTrailingZeros().toPlainString() + ")" : "");
        }
        return null;
    }

    static BigDecimal waivedAmount(BigDecimal fee, int percent) {
        return fee.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void requireWaivableOffer(AdmissionOffer offer) {
        if (offer.getOfferStatus() != OfferStatus.DISPATCHED || AdmissionOfferService.isPastValidity(offer)) {
            throw new BadRequestException("Waivers can only be applied to a dispatched, unexpired offer; offer "
                    + offer.getOfferCode() + " is " + (AdmissionOfferService.isPastValidity(offer) ? "expired" : offer.getOfferStatus()));
        }
    }

    private FeeWaiverResponse map(FeeWaiver w, AdmissionOffer offer) {
        Optional<AdmissionEnquiry> enquiry = admissionEnquiryRepository.findByEnquiryId(w.getEnquiryId());
        return FeeWaiverResponse.builder()
                .waiverId(w.getWaiverId())
                .tenantId(w.getTenantId())
                .offerId(w.getOfferId())
                .offerCode(offer != null ? offer.getOfferCode() : null)
                .offerStatus(offer != null ? offer.getOfferStatus().name() : null)
                .enquiryId(w.getEnquiryId())
                .applicationNumber(enquiry.map(AdmissionEnquiry::getApplicationNumber).orElse(null))
                .candidateName(enquiry.map(e -> e.getCandidateFirstName() + " " + e.getCandidateLastName()).orElse(null))
                .scholarshipCategory(w.getScholarshipCategory().name())
                .waiverPercent(w.getWaiverPercent())
                .originalAmount(w.getOriginalAmount())
                .waivedAmount(w.getWaivedAmount())
                .payableAmount(w.getPayableAmount())
                .justification(w.getJustification())
                .status(w.getStatus().name())
                .requestedBy(w.getRequestedBy())
                .decidedBy(w.getDecidedBy())
                .decidedAt(w.getDecidedAt())
                .decisionNotes(w.getDecisionNotes())
                .waiverReceiptNumber(w.getWaiverReceiptNumber())
                .createdAt(w.getCreatedAt())
                .build();
    }
}
