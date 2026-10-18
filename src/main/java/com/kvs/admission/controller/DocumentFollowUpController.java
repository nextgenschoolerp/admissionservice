package com.kvs.admission.controller;

import com.kvs.admission.dto.DocumentFollowUpResponse;
import com.kvs.admission.dto.DocumentFollowUpScanResponse;
import com.kvs.admission.dto.ResolveFollowUpRequest;
import com.kvs.admission.dto.ScanDocumentFollowUpsRequest;
import com.kvs.admission.enums.FollowUpStatus;
import com.kvs.admission.service.DocumentFollowUpService;
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
 * Controller for document completeness follow-ups.
 * Covers user stories:
 * US-02011, US-02051, US-02091: Flag incomplete applicant document submissions for follow-up verification
 * Actor: Registrar (Marketing Manager in US-02031 / US-02071 not granted: document verification is not a marketing duty)
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/document-follow-ups")
public class DocumentFollowUpController {

    @Autowired
    private DocumentFollowUpService followUpService;

    /**
     * Scan a branch and flag applicants with incomplete mandatory documents (US-02011).
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DocumentFollowUpScanResponse>> scan(@Valid @RequestBody ScanDocumentFollowUpsRequest request) {
        log.info("Request to scan document completeness for tenant {}, branch {}", request.getTenantId(), request.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Document completeness scan finished", followUpService.scan(request)));
    }

    /**
     * List follow-ups of a branch, optionally by status.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('REGISTRAR', 'ADMISSION_OFFICER', 'FRONT_OFFICE_COUNSELOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentFollowUpResponse>>> list(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("branchId") String branchId,
            @RequestParam(value = "status", required = false) FollowUpStatus status) {
        return ResponseEntity.ok(ApiResponse.success(followUpService.list(tenantId, branchId, status)));
    }

    /**
     * Resolve an open follow-up manually.
     */
    @PatchMapping("/{followUpId}/resolution")
    @PreAuthorize("hasAnyRole('REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DocumentFollowUpResponse>> resolve(@PathVariable("followUpId") UUID followUpId,
                                                                         @Valid @RequestBody ResolveFollowUpRequest request) {
        log.info("Request to resolve document follow-up {}", followUpId);
        return ResponseEntity.ok(ApiResponse.success("Follow-up resolved", followUpService.resolve(followUpId, request)));
    }
}
