package com.kvs.admission.controller;

import com.kvs.admission.dto.FeeWaiverDecisionRequest;
import com.kvs.admission.dto.FeeWaiverResponse;
import com.kvs.admission.dto.RequestFeeWaiverRequest;
import com.kvs.admission.enums.WaiverStatus;
import com.kvs.admission.service.FeeWaiverService;
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
 * Controller for scholarship fee waivers.
 * Covers user stories:
 * US-02016, US-02056, US-02096: Process application fee waivers for scholarship candidates
 * Actor: School Principal decides; admissions staff may request. Entrance Exam Evaluator (US-02036/76) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class FeeWaiverController {

    @Autowired
    private FeeWaiverService waiverService;

    /**
     * Request a scholarship waiver on an offer's seat reservation fee.
     */
    @PostMapping("/offers/{offerId}/fee-waivers")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FeeWaiverResponse>> request(@PathVariable("offerId") UUID offerId,
                                                                  @Valid @RequestBody RequestFeeWaiverRequest request) {
        log.info("Request for {} waiver of {}% on offer {}", request.getScholarshipCategory(), request.getWaiverPercent(), offerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee waiver requested", waiverService.requestWaiver(offerId, request)));
    }

    /**
     * Approve or reject a waiver (US-02016).
     */
    @PatchMapping("/fee-waivers/{waiverId}/decision")
    @PreAuthorize("hasAnyRole('SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FeeWaiverResponse>> decide(@PathVariable("waiverId") UUID waiverId,
                                                                 @Valid @RequestBody FeeWaiverDecisionRequest request) {
        log.info("Request to mark fee waiver {} as {}", waiverId, request.getDecision());
        return ResponseEntity.ok(ApiResponse.success("Fee waiver decision recorded", waiverService.decide(waiverId, request)));
    }

    @GetMapping("/fee-waivers/{waiverId}")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SCHOOL_PRINCIPAL', 'FINANCE_CASHIER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FeeWaiverResponse>> get(@PathVariable("waiverId") UUID waiverId) {
        return ResponseEntity.ok(ApiResponse.success(waiverService.getWaiver(waiverId)));
    }

    @GetMapping("/fee-waivers")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SCHOOL_PRINCIPAL', 'FINANCE_CASHIER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<FeeWaiverResponse>>> list(@RequestParam("tenantId") String tenantId,
                                                                     @RequestParam(value = "status", required = false) WaiverStatus status) {
        return ResponseEntity.ok(ApiResponse.success(waiverService.listWaivers(tenantId, status)));
    }
}
