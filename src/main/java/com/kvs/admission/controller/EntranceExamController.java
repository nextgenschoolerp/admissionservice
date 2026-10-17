package com.kvs.admission.controller;

import com.kvs.admission.dto.AdmitCardResponse;
import com.kvs.admission.dto.EntranceExamResponse;
import com.kvs.admission.dto.ScheduleEntranceExamRequest;
import com.kvs.admission.service.EntranceExamService;
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
 * Controller managing entrance exam scheduling and admit cards.
 * Covers user story:
 * US-02002: Schedule entrance examination dates and auto-generate candidate admit cards
 * Actor: Admission Officer
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/entrance-exams")
public class EntranceExamController {

    @Autowired
    private EntranceExamService entranceExamService;

    /**
     * Schedule an entrance exam and auto-generate admit cards for the listed candidates (US-02002).
     *
     * @param request validated exam schedule request
     * @return 201 Created with the exam and its admit cards
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EntranceExamResponse>> scheduleExam(
            @Valid @RequestBody ScheduleEntranceExamRequest request) {
        log.info("Request to schedule entrance exam '{}' for {} candidates",
                request.getExamTitle(), request.getEnquiryIds().size());
        EntranceExamResponse response = entranceExamService.scheduleExam(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Entrance exam scheduled and admit cards generated successfully", response));
    }

    /**
     * Retrieve an entrance exam with its admit cards.
     *
     * @param examId the exam UUID
     * @return 200 OK with exam details
     */
    @GetMapping("/{examId}")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'FRONT_OFFICE_COUNSELOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EntranceExamResponse>> getExam(@PathVariable("examId") UUID examId) {
        log.info("Request to fetch entrance exam: {}", examId);
        return ResponseEntity.ok(ApiResponse.success(entranceExamService.getExam(examId)));
    }

    /**
     * List the admit cards generated for an entrance exam.
     *
     * @param examId the exam UUID
     * @return 200 OK with admit cards ordered by roll number
     */
    @GetMapping("/{examId}/admit-cards")
    @PreAuthorize("hasAnyRole('ADMISSION_OFFICER', 'FRONT_OFFICE_COUNSELOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AdmitCardResponse>>> listAdmitCards(@PathVariable("examId") UUID examId) {
        log.info("Request to list admit cards for entrance exam: {}", examId);
        return ResponseEntity.ok(ApiResponse.success(entranceExamService.listAdmitCards(examId)));
    }
}
