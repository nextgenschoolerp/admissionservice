package com.kvs.admission.service;

import com.kvs.admission.dto.CollectDepositRequest;
import com.kvs.admission.dto.DepositReceiptResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmissionFeeDeposit;
import com.kvs.admission.entity.AdmissionOffer;
import com.kvs.admission.enums.OfferStatus;
import com.kvs.admission.enums.PaymentMode;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmissionFeeDepositRepository;
import com.kvs.admission.repo.AdmissionOfferRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for collecting seat reservation deposits and issuing digital receipts.
 * Covers user story:
 * US-02008: Collect seat reservation deposit fees and issue digital receipts
 * Actor: School Principal
 */
@Service
@Slf4j
public class AdmissionFeeDepositService {

    @Autowired
    private AdmissionFeeDepositRepository depositRepository;

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Records the seat reservation deposit for a dispatched, still-valid offer, accepts the offer
     * and returns the digital receipt. The amount must equal the offer's initial fee; non-cash
     * payments need a unique transaction reference.
     *
     * @param offerId the offer UUID
     * @param request the payment details
     * @return the digital receipt
     */
    @Transactional
    public DepositReceiptResponse collectDeposit(UUID offerId, CollectDepositRequest request) {
        log.info("Collecting seat reservation deposit for offer: {} via {}", offerId, request.getPaymentMode());
        AdmissionOffer offer = offerRepository.findByOfferIdForUpdate(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionOffer", "offerId", offerId));

        if (offer.getOfferStatus() == OfferStatus.ACCEPTED || depositRepository.existsByOfferId(offerId)) {
            throw new ConflictException("The seat reservation deposit for offer " + offer.getOfferCode()
                    + " has already been collected");
        }
        if (offer.getOfferStatus() != OfferStatus.DISPATCHED) {
            throw new BadRequestException("Deposit cannot be collected for offer " + offer.getOfferCode()
                    + " in status " + offer.getOfferStatus());
        }
        if (AdmissionOfferService.isPastValidity(offer)) {
            throw new BadRequestException("Offer " + offer.getOfferCode() + " expired on " + offer.getValidUntilDate());
        }
        if (request.getAmountPaid().compareTo(offer.getInitialFeeAmount()) != 0) {
            throw new BadRequestException("Amount paid " + request.getAmountPaid().toPlainString()
                    + " must equal the seat reservation fee of " + offer.getInitialFeeAmount().toPlainString());
        }

        String reference = StringUtils.hasText(request.getTransactionReference())
                ? request.getTransactionReference().trim()
                : null;
        if (request.getPaymentMode() != PaymentMode.CASH && reference == null) {
            throw new BadRequestException("Transaction reference is required for " + request.getPaymentMode()
                    + " payments");
        }
        if (reference != null && depositRepository.existsByTenantIdAndTransactionReference(offer.getTenantId(), reference)) {
            throw new ConflictException("Transaction reference " + reference + " has already been used");
        }

        AdmissionFeeDeposit deposit = new AdmissionFeeDeposit();
        deposit.setTenantId(offer.getTenantId());
        deposit.setOfferId(offer.getOfferId());
        deposit.setEnquiryId(offer.getEnquiryId());
        deposit.setReceiptNumber(ReferenceNumberGenerator.next("RCPT"));
        deposit.setAmountPaid(request.getAmountPaid());
        deposit.setCurrency(offer.getCurrency());
        deposit.setPaymentMode(request.getPaymentMode());
        deposit.setTransactionReference(reference);
        deposit.setPaymentTimestamp(Instant.now());
        deposit.setCollectedBy(SecurityUtils.getCurrentUserEmail().orElse("school_principal"));
        deposit.setRemarks(request.getRemarks());
        AdmissionFeeDeposit saved = depositRepository.save(deposit);

        offer.setOfferStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedAt(saved.getPaymentTimestamp());
        offerRepository.save(offer);

        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(offer.getEnquiryId()).orElse(null);
        log.info("Collected deposit {} for offer {}", saved.getReceiptNumber(), offer.getOfferCode());
        auditService.logSuccess("admission_fee_deposit:" + offer.getTenantId(), "COLLECT_SEAT_DEPOSIT",
                "Collected " + saved.getCurrency() + " " + saved.getAmountPaid().toPlainString()
                        + " via " + saved.getPaymentMode()
                        + " for offer " + offer.getOfferCode()
                        + " | Receipt: " + saved.getReceiptNumber()
                        + (reference != null ? " | Txn: " + reference : ""));

        return mapToReceipt(saved, offer, enquiry);
    }

    /**
     * Retrieves a digital receipt by receipt number.
     *
     * @param receiptNumber the receipt number
     * @return the digital receipt
     */
    @Transactional(readOnly = true)
    public DepositReceiptResponse getReceipt(String receiptNumber) {
        log.info("Fetching deposit receipt: {}", receiptNumber);
        AdmissionFeeDeposit deposit = depositRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionFeeDeposit", "receiptNumber", receiptNumber));
        AdmissionOffer offer = offerRepository.findByOfferId(deposit.getOfferId()).orElse(null);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(deposit.getEnquiryId()).orElse(null);
        return mapToReceipt(deposit, offer, enquiry);
    }

    private DepositReceiptResponse mapToReceipt(AdmissionFeeDeposit deposit, AdmissionOffer offer,
                                                AdmissionEnquiry enquiry) {
        return DepositReceiptResponse.builder()
                .depositId(deposit.getDepositId())
                .receiptNumber(deposit.getReceiptNumber())
                .tenantId(deposit.getTenantId())
                .offerId(deposit.getOfferId())
                .offerCode(offer != null ? offer.getOfferCode() : null)
                .offerStatus(offer != null ? offer.getOfferStatus().name() : null)
                .enquiryId(deposit.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .candidateName(enquiry != null
                        ? enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName()
                        : null)
                .guardianName(enquiry != null ? enquiry.getGuardianName() : null)
                .offeredGradeId(offer != null ? offer.getOfferedGradeId() : null)
                .academicYearId(offer != null ? offer.getAcademicYearId() : null)
                .amountPaid(deposit.getAmountPaid())
                .currency(deposit.getCurrency())
                .paymentMode(deposit.getPaymentMode().name())
                .transactionReference(deposit.getTransactionReference())
                .paymentTimestamp(deposit.getPaymentTimestamp())
                .collectedBy(deposit.getCollectedBy())
                .remarks(deposit.getRemarks())
                .build();
    }
}
