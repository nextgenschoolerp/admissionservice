package com.kvs.admission.controller;

import com.kvs.admission.dto.*;
import com.kvs.admission.service.WaitlistService;
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
 * Controller for grade waitlists and seat escalation.
 * Covers user stories:
 * US-02014, US-02034, US-02054, US-02074, US-02094: Manage applicant waitlist queues and automate seat escalation rules
 * Actor: Admission Officer (US-02034, US-02074). Finance Cashier (US-02014/54/94) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/waitlists")
public class WaitlistController {

    @Autowired
    private WaitlistService waitlistService;

    /**
     * Create or update the seat capacity and escalation rule of a grade.
     */
    @PutMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WaitlistResponse>> saveRule(@Valid @RequestBody WaitlistRuleRequest request) {
        log.info("Request to save waitlist rule for grade {} ({})", request.getGradeId(), request.getAcademicYearId());
        return ResponseEntity.ok(ApiResponse.success("Waitlist rule saved", waitlistService.saveRule(request)));
    }

    /**
     * View a grade waitlist with seat occupancy and queue positions.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WaitlistResponse>> get(@RequestParam("tenantId") String tenantId,
                                                             @RequestParam("branchId") String branchId,
                                                             @RequestParam("academicYearId") String academicYearId,
                                                             @RequestParam("gradeId") String gradeId) {
        return ResponseEntity.ok(ApiResponse.success(waitlistService.getWaitlist(tenantId, branchId, academicYearId, gradeId)));
    }

    /**
     * Add an evaluated candidate to their grade waitlist.
     */
    @PostMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WaitlistEntryResponse>> add(@Valid @RequestBody AddToWaitlistRequest request) {
        log.info("Request to waitlist enquiry {}", request.getEnquiryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Candidate added to the waitlist", waitlistService.addToWaitlist(request)));
    }

    /**
     * Remove a waiting candidate from the queue.
     */
    @DeleteMapping("/entries/{entryId}")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WaitlistEntryResponse>> remove(@PathVariable("entryId") UUID entryId,
                                                                     @RequestParam(value = "reason", required = false) String reason) {
        log.info("Request to remove waitlist entry {}", entryId);
        return ResponseEntity.ok(ApiResponse.success("Candidate removed from the waitlist",
                waitlistService.removeFromWaitlist(entryId, reason)));
    }

    /**
     * Release lapsed offers and offer the free seats to the top of the queue (US-02014).
     */
    @PostMapping("/escalation")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<WaitlistEscalationResponse>> escalate(@Valid @RequestBody WaitlistScopeRequest request) {
        log.info("Request to escalate waitlist of grade {} ({})", request.getGradeId(), request.getAcademicYearId());
        return ResponseEntity.ok(ApiResponse.success("Waitlist escalation finished", waitlistService.escalate(request)));
    }
}
