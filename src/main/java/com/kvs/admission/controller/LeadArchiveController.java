package com.kvs.admission.controller;

import com.kvs.admission.dto.ArchiveLeadsRequest;
import com.kvs.admission.dto.ArchiveLeadsResponse;
import com.kvs.admission.dto.ArchivedLeadTrendResponse;
import com.kvs.admission.service.LeadArchiveService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller for archiving closed leads and their historical trends.
 * Covers user stories:
 * US-02020, US-02040, US-02060, US-02080, US-02100: Archive rejected inquiry leads for historical trend reporting
 * Actor: School Principal (US-02040/80). Entrance Exam Evaluator (US-02020/60/100) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class LeadArchiveController {

    @Autowired
    private LeadArchiveService archiveService;

    /**
     * Archive rejected (and withdrawn) leads that have been closed long enough (US-02020).
     */
    @PostMapping("/lead-archives")
    @PreAuthorize("hasAnyRole('SCHOOL_PRINCIPAL', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArchiveLeadsResponse>> archive(@Valid @RequestBody ArchiveLeadsRequest request) {
        log.info("Request to archive closed leads for tenant {}", request.getTenantId());
        return ResponseEntity.ok(ApiResponse.success("Closed leads archived", archiveService.archive(request)));
    }

    /**
     * Monthly trend of archived leads.
     */
    @GetMapping("/analytics/archived-lead-trends")
    @PreAuthorize("hasAnyRole('SCHOOL_PRINCIPAL', 'ADMISSION_OFFICER', 'MARKETING_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ArchivedLeadTrendResponse>> trends(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(archiveService.trends(tenantId, fromDate, toDate)));
    }
}
