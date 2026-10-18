package com.kvs.admission.controller;

import com.kvs.admission.dto.ConvertLeadRequest;
import com.kvs.admission.dto.StudentMasterRecordResponse;
import com.kvs.admission.service.StudentConversionService;
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
 * Controller converting verified leads into active Student Master records.
 * Covers user story:
 * US-02005: Convert verified candidate leads into active Student Master records
 * Actor: Applicant Parent (US-02045, US-02085); Front-Office Counselor (US-02025, US-02065)
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class StudentConversionController {

    @Autowired
    private StudentConversionService conversionService;

    /**
     * Convert a verified candidate lead into an active Student Master record (US-02005).
     *
     * @param enquiryId the enquiry UUID
     * @param request   validated student details
     * @return 201 Created with the student record
     */
    @PostMapping("/enquiries/{enquiryId}/student-conversion")
    @PreAuthorize("hasAnyRole('APPLICANT_PARENT', 'FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'REGISTRAR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StudentMasterRecordResponse>> convertLead(
            @PathVariable("enquiryId") UUID enquiryId,
            @Valid @RequestBody ConvertLeadRequest request) {
        log.info("Request to convert lead {} into a Student Master record", enquiryId);
        StudentMasterRecordResponse response = conversionService.convertLead(enquiryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lead converted into an active Student Master record", response));
    }

    /**
     * Retrieve a Student Master record.
     *
     * @param studentId the student UUID
     * @return 200 OK with the student record
     */
    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('APPLICANT_PARENT', 'FRONT_OFFICE_COUNSELOR', 'ADMISSION_OFFICER', 'REGISTRAR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StudentMasterRecordResponse>> getStudent(
            @PathVariable("studentId") UUID studentId) {
        log.info("Request to fetch Student Master record: {}", studentId);
        return ResponseEntity.ok(ApiResponse.success(conversionService.getStudent(studentId)));
    }
}
