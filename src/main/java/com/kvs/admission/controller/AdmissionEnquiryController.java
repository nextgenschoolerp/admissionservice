package com.kvs.admission.controller;

import com.kvs.admission.dto.AdmissionEnquiryResponse;
import com.kvs.admission.dto.CaptureEnquiryRequest;
import com.kvs.admission.service.AdmissionEnquiryService;
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
 * Controller managing admission enquiry / walk-in lead capture.
 * Covers user story:
 * US-02001: Capture walk-in inquiry lead details directly into the CRM database
 * Actor: Front-Office Counselor (US-02041, US-02081); Applicant Parent for their own enquiries (US-02021, US-02061)
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/enquiries")
public class AdmissionEnquiryController {

    @Autowired
    private AdmissionEnquiryService admissionEnquiryService;

    /**
     * Capture a walk-in inquiry lead directly into the CRM database (US-02001).
     *
     * @param request validated walk-in enquiry capture request
     * @return 201 Created with the persisted enquiry details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'APPLICANT_PARENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionEnquiryResponse>> captureEnquiry(
            @Valid @RequestBody CaptureEnquiryRequest request) {
        log.info("Request to capture walk-in enquiry for candidate: {} {}",
                request.getCandidateFirstName(), request.getCandidateLastName());
        AdmissionEnquiryResponse response = admissionEnquiryService.captureWalkInEnquiry(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Walk-in inquiry lead captured successfully into the CRM database", response));
    }

    /**
     * Retrieve a specific admission enquiry by its UUID.
     *
     * @param enquiryId the enquiry UUID
     * @return 200 OK with the enquiry details
     */
    @GetMapping("/{enquiryId}")
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'APPLICANT_PARENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionEnquiryResponse>> getEnquiry(
            @PathVariable("enquiryId") UUID enquiryId) {
        log.info("Request to fetch admission enquiry: {}", enquiryId);
        AdmissionEnquiryResponse response = admissionEnquiryService.getEnquiryById(enquiryId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * List the active (not archived) enquiries for a given tenant and branch.
     *
     * @param tenantId the tenant identifier
     * @param branchId the branch identifier
     * @return 200 OK with list of enquiries
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AdmissionEnquiryResponse>>> listEnquiries(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("branchId") String branchId) {
        log.info("Request to list enquiries for tenantId: {}, branchId: {}", tenantId, branchId);
        List<AdmissionEnquiryResponse> response = admissionEnquiryService.listEnquiries(tenantId, branchId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
