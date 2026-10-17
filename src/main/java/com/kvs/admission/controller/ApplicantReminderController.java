package com.kvs.admission.controller;

import com.kvs.admission.dto.DispatchRemindersRequest;
import com.kvs.admission.dto.ReminderDispatchSummaryResponse;
import com.kvs.admission.service.ApplicantReminderService;
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
 * Controller for applicant WhatsApp reminders.
 * Covers user story:
 * US-02009: Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 * Actor: Front-Office Counselor
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/reminders")
public class ApplicantReminderController {

    @Autowired
    private ApplicantReminderService reminderService;

    /**
     * Dispatch WhatsApp reminders for entrance evaluations starting within the lookahead window (US-02009).
     *
     * @param request validated dispatch request
     * @return 200 OK with the dispatch summary
     */
    @PostMapping("/entrance-evaluations")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReminderDispatchSummaryResponse>> dispatchReminders(
            @Valid @RequestBody DispatchRemindersRequest request) {
        log.info("Request to dispatch entrance evaluation reminders for tenant {}, branch {}",
                request.getTenantId(), request.getBranchId());
        ReminderDispatchSummaryResponse response = reminderService.dispatchEntranceEvaluationReminders(
                request.getTenantId(), request.getBranchId(), request.getLookaheadHours());
        return ResponseEntity.ok(ApiResponse.success("Entrance evaluation reminders dispatched", response));
    }
}
