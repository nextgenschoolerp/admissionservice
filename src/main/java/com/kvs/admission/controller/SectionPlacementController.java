package com.kvs.admission.controller;

import com.kvs.admission.dto.*;
import com.kvs.admission.service.SectionPlacementService;
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
 * Controller for grade sections and student placement.
 * Covers user stories:
 * US-02013, US-02033, US-02053, US-02073, US-02093: Assign target grade levels and sections to confirmed admission leads
 * Placement is restricted to the Admission Officer and Registrar. The backlog actors (Applicant Parent in
 * US-02013/53/93, Front-Office Counselor in US-02033/73) are not granted write access; parents see the
 * placement through GET /students/{studentId}.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class SectionPlacementController {

    @Autowired
    private SectionPlacementService placementService;

    /**
     * Create or update the sections of a grade with their capacities.
     */
    @PutMapping("/grade-sections")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<GradeSectionResponse>>> configure(@Valid @RequestBody ConfigureGradeSectionsRequest request) {
        log.info("Request to configure {} sections for grade {}", request.getSections().size(), request.getGradeId());
        return ResponseEntity.ok(ApiResponse.success("Grade sections saved", placementService.configureSections(request)));
    }

    /**
     * List the sections of a grade with occupancy.
     */
    @GetMapping("/grade-sections")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'FRONT_OFFICE_COUNSELOR', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<GradeSectionResponse>>> list(@RequestParam("tenantId") String tenantId,
                                                                        @RequestParam("branchId") String branchId,
                                                                        @RequestParam("academicYearId") String academicYearId,
                                                                        @RequestParam("gradeId") String gradeId) {
        return ResponseEntity.ok(ApiResponse.success(placementService.listSections(tenantId, branchId, academicYearId, gradeId)));
    }

    /**
     * Place every unplaced enrolled student of a grade into the section with the most free seats (US-02013).
     */
    @PostMapping("/grade-sections/auto-placement")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AutoPlacementResponse>> autoPlace(@Valid @RequestBody AutoPlacementRequest request) {
        log.info("Request to auto-place students of grade {} ({})", request.getGradeId(), request.getAcademicYearId());
        return ResponseEntity.ok(ApiResponse.success("Students placed into sections", placementService.autoPlace(request)));
    }

    /**
     * Place or move one student into a grade and section.
     */
    @PutMapping("/students/{studentId}/placement")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'REGISTRAR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StudentMasterRecordResponse>> place(@PathVariable("studentId") UUID studentId,
                                                                         @Valid @RequestBody PlaceStudentRequest request) {
        log.info("Request to place student {} into {}-{}", studentId, request.getGradeId(), request.getSectionCode());
        return ResponseEntity.ok(ApiResponse.success("Student placed", placementService.placeStudent(studentId, request)));
    }
}
