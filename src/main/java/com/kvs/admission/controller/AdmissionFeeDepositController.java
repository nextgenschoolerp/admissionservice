package com.kvs.admission.controller;

import com.kvs.admission.dto.CollectDepositRequest;
import com.kvs.admission.dto.DepositReceiptResponse;
import com.kvs.admission.service.AdmissionFeeDepositService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for seat reservation deposits and digital receipts.
 * Covers user story:
 * US-02008: Collect seat reservation deposit fees and issue digital receipts
 * Actor: School Principal
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class AdmissionFeeDepositController {

    @Autowired
    private AdmissionFeeDepositService depositService;

    /**
     * Collect the seat reservation deposit for an offer and issue a digital receipt (US-02008).
     *
     * @param offerId the offer UUID
     * @param request validated payment details
     * @return 201 Created with the digital receipt
     */
    @PostMapping("/offers/{offerId}/deposits")
    @PreAuthorize("hasAnyRole('SCHOOL_PRINCIPAL', 'FINANCE_CASHIER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DepositReceiptResponse>> collectDeposit(
            @PathVariable("offerId") UUID offerId,
            @Valid @RequestBody CollectDepositRequest request) {
        log.info("Request to collect seat reservation deposit for offer: {}", offerId);
        DepositReceiptResponse response = depositService.collectDeposit(offerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seat reservation deposit collected and digital receipt issued", response));
    }

    /**
     * Retrieve a digital receipt.
     *
     * @param receiptNumber the receipt number
     * @return 200 OK with the receipt
     */
    @GetMapping("/deposits/receipts/{receiptNumber}")
    @PreAuthorize("hasAnyRole('SCHOOL_PRINCIPAL', 'FINANCE_CASHIER', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DepositReceiptResponse>> getReceipt(
            @PathVariable("receiptNumber") String receiptNumber) {
        log.info("Request to fetch deposit receipt: {}", receiptNumber);
        return ResponseEntity.ok(ApiResponse.success(depositService.getReceipt(receiptNumber)));
    }
}
