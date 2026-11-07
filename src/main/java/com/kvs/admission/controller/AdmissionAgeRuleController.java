package com.kvs.admission.controller;

import com.kvs.admission.dto.AgeCheckResponse;
import com.kvs.admission.dto.AgeRuleResponse;
import com.kvs.admission.dto.SaveAgeRuleRequest;
import com.kvs.admission.service.AdmissionAgeRuleService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admission age rules per board (Settings → Academic setup → Admission rules, and the age notice on an application): the minimum
 * age for a class on a cut-off date. Admission staff and leadership read them and check a child's age; the registrar, admission
 * officer and leadership set a school's own rule.
 */
@RestController
@RequestMapping("/api/v1/admissions/age-rules")
@RequiredArgsConstructor
public class AdmissionAgeRuleController {

    private static final String READERS = "hasAnyRole('FRONT_OFFICE_COUNSELOR', 'REGISTRAR', 'ADMISSION_OFFICER', 'TRUSTEE', 'PRINCIPAL', 'SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'SUPER_ADMIN')";
    private static final String EDITORS = "hasAnyRole('REGISTRAR', 'ADMISSION_OFFICER', 'TRUSTEE', 'PRINCIPAL', 'SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'SUPER_ADMIN')";

    private final AdmissionAgeRuleService service;

    @GetMapping
    @PreAuthorize(READERS)
    public ResponseEntity<ApiResponse<List<AgeRuleResponse>>> rules(@RequestParam("tenantId") String tenantId, @RequestParam("boardCode") String boardCode) {
        return ResponseEntity.ok(ApiResponse.success(service.effective(tenantId, boardCode)));
    }

    @GetMapping("/check")
    @PreAuthorize(READERS)
    public ResponseEntity<ApiResponse<AgeCheckResponse>> check(@RequestParam("tenantId") String tenantId, @RequestParam("boardCode") String boardCode,
                                                               @RequestParam("className") String className,
                                                               @RequestParam("dateOfBirth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                                                               @RequestParam("academicYear") String academicYear) {
        return ResponseEntity.ok(ApiResponse.success(service.check(tenantId, boardCode, className, dateOfBirth, academicYear)));
    }

    @PutMapping("/{boardCode}/{className}")
    @PreAuthorize(EDITORS)
    public ResponseEntity<ApiResponse<AgeRuleResponse>> save(@PathVariable("boardCode") String boardCode, @PathVariable("className") String className,
                                                             @Valid @RequestBody SaveAgeRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Age rule saved", service.save(boardCode, className, request)));
    }

    @DeleteMapping("/{boardCode}/{className}")
    @PreAuthorize(EDITORS)
    public ResponseEntity<ApiResponse<Void>> reset(@PathVariable("boardCode") String boardCode, @PathVariable("className") String className,
                                                   @RequestParam("tenantId") String tenantId) {
        service.reset(tenantId, boardCode, className);
        return ResponseEntity.ok(ApiResponse.success("The board's rule applies again", null));
    }
}
