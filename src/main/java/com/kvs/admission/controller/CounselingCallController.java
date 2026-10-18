package com.kvs.admission.controller;

import com.kvs.admission.dto.CallbackResponse;
import com.kvs.admission.dto.CancelCallbackRequest;
import com.kvs.admission.dto.CounselingCallResponse;
import com.kvs.admission.dto.LogCallRequest;
import com.kvs.admission.service.CounselingCallService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controller for counseling call logs and callbacks.
 * Covers user stories:
 * US-02015, US-02035, US-02055, US-02075, US-02095: Log telephone counseling call notes and schedule automated callbacks
 * Write: Front-Office Counselor, Admission Officer. Read: also Marketing Manager (US-02015/55/95).
 * The Registrar (US-02035/75) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class CounselingCallController {

    @Autowired
    private CounselingCallService callService;

    /**
     * Log a counseling call; a callback is scheduled automatically from the outcome (US-02015).
     */
    @PostMapping("/enquiries/{enquiryId}/calls")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CounselingCallResponse>> logCall(@PathVariable("enquiryId") UUID enquiryId,
                                                                       @Valid @RequestBody LogCallRequest request) {
        log.info("Request to log {} call ({}) for enquiry {}", request.getDirection(), request.getOutcome(), enquiryId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Call logged", callService.logCall(enquiryId, request)));
    }

    /**
     * Call history of an enquiry.
     */
    @GetMapping("/enquiries/{enquiryId}/calls")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'MARKETING_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CounselingCallResponse>>> listCalls(@PathVariable("enquiryId") UUID enquiryId) {
        return ResponseEntity.ok(ApiResponse.success(callService.listCalls(enquiryId)));
    }

    /**
     * Scheduled callbacks that are due, optionally for one counselor.
     */
    @GetMapping("/callbacks")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'MARKETING_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CallbackResponse>>> dueCallbacks(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(value = "assignedTo", required = false) String assignedTo,
            @RequestParam(value = "dueBefore", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dueBefore) {
        return ResponseEntity.ok(ApiResponse.success(callService.dueCallbacks(tenantId, assignedTo, dueBefore)));
    }

    /**
     * Cancel a scheduled callback.
     */
    @PatchMapping("/callbacks/{callbackId}/cancellation")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CallbackResponse>> cancel(@PathVariable("callbackId") UUID callbackId,
                                                                @Valid @RequestBody CancelCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Callback cancelled", callService.cancelCallback(callbackId, request)));
    }
}
