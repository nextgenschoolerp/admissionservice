package com.kvs.admission.controller;

import com.kvs.admission.dto.AdmissionOfferResponse;
import com.kvs.admission.dto.IssueOfferRequest;
import com.kvs.admission.service.AdmissionOfferService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for admission offer letters.
 * Covers user story:
 * US-02006: Issue automated offer letters with embedded online fee payment links
 * Actor: Finance Cashier
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class AdmissionOfferController {

    @Autowired
    private AdmissionOfferService offerService;

    /**
     * Issue an offer letter with an embedded online fee payment link (US-02006).
     *
     * @param request validated offer request
     * @return 201 Created with the dispatched offer
     */
    @PostMapping("/offers")
    @PreAuthorize("hasAnyRole('FINANCE_CASHIER', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionOfferResponse>> issueOffer(@Valid @RequestBody IssueOfferRequest request) {
        log.info("Request to issue admission offer for enquiry: {}", request.getEnquiryId());
        AdmissionOfferResponse response = offerService.issueOffer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Offer letter issued with online fee payment link", response));
    }

    /**
     * Retrieve an admission offer.
     *
     * @param offerId the offer UUID
     * @return 200 OK with the offer
     */
    @GetMapping("/offers/{offerId}")
    @PreAuthorize("hasAnyRole('FINANCE_CASHIER', 'ADMISSION_OFFICER', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionOfferResponse>> getOffer(@PathVariable("offerId") UUID offerId) {
        log.info("Request to fetch admission offer: {}", offerId);
        return ResponseEntity.ok(ApiResponse.success(offerService.getOffer(offerId)));
    }

    /**
     * List the offers issued for an enquiry.
     *
     * @param enquiryId the enquiry UUID
     * @return 200 OK with offers, newest first
     */
    @GetMapping("/enquiries/{enquiryId}/offers")
    @PreAuthorize("hasAnyRole('FINANCE_CASHIER', 'ADMISSION_OFFICER', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AdmissionOfferResponse>>> listOffers(
            @PathVariable("enquiryId") UUID enquiryId) {
        log.info("Request to list offers for enquiry: {}", enquiryId);
        return ResponseEntity.ok(ApiResponse.success(offerService.listOffers(enquiryId)));
    }
}
