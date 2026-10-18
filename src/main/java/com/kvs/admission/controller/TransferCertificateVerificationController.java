package com.kvs.admission.controller;

import com.kvs.admission.dto.TcVerificationResponse;
import com.kvs.admission.dto.VerifyTransferCertificateRequest;
import com.kvs.admission.service.TransferCertificateVerificationService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for transfer certificate checks with education boards.
 * Covers user stories:
 * US-02017, US-02057, US-02097: Verify transfer certificate validity against education board databases
 * Actor: Front-Office Counselor. Applicant Parent (US-02037/77) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/enquiries/{enquiryId}/transfer-certificate/verifications")
public class TransferCertificateVerificationController {

    @Autowired
    private TransferCertificateVerificationService verificationService;

    /**
     * Check the uploaded transfer certificate with the issuing board (US-02017).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TcVerificationResponse>> verify(@PathVariable("enquiryId") UUID enquiryId,
                                                                      @Valid @RequestBody VerifyTransferCertificateRequest request) {
        log.info("Request to verify transfer certificate {} ({}) for enquiry {}", request.getTcNumber(), request.getBoard(), enquiryId);
        return ResponseEntity.ok(ApiResponse.success("Transfer certificate checked with the board",
                verificationService.verify(enquiryId, request)));
    }

    /**
     * Board lookups made for an enquiry.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<TcVerificationResponse>>> history(@PathVariable("enquiryId") UUID enquiryId) {
        return ResponseEntity.ok(ApiResponse.success(verificationService.history(enquiryId)));
    }
}
