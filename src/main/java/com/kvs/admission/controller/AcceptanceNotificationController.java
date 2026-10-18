package com.kvs.admission.controller;

import com.kvs.admission.dto.BulkAcceptanceRequest;
import com.kvs.admission.dto.BulkAcceptanceResponse;
import com.kvs.admission.service.AcceptanceNotificationService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for bulk acceptance notifications.
 * Covers user stories:
 * US-02018, US-02058, US-02098: Send bulk acceptance notifications to qualified applicant pools
 * Actor: Admission Officer. Finance Cashier (US-02038/78) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/acceptance-notifications")
public class AcceptanceNotificationController {

    @Autowired
    private AcceptanceNotificationService acceptanceService;

    /**
     * Notify every applicant holding a live offer in the grade (US-02018).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BulkAcceptanceResponse>> notifyPool(@Valid @RequestBody BulkAcceptanceRequest request) {
        log.info("Request to send acceptance notifications for grade {} ({})", request.getGradeId(), request.getAcademicYearId());
        return ResponseEntity.ok(ApiResponse.success("Acceptance notifications dispatched", acceptanceService.notifyPool(request)));
    }
}
