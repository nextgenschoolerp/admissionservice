package com.kvs.admission.controller;

import com.kvs.admission.config.AdmissionPerms;
import com.kvs.admission.dto.EvaluationResponse;
import com.kvs.admission.dto.RecordEvaluationRequest;
import com.kvs.admission.dto.RubricCriterionResponse;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.service.ApplicantEvaluationService;
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
 * Controller for applicant evaluations scored against the standardized rubric.
 * Covers user story:
 * US-02003: Evaluate applicant interview scores using standardized rubric criteria
 * Actor: Registrar
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class ApplicantEvaluationController {

    @Autowired
    private ApplicantEvaluationService evaluationService;

    /**
     * Retrieve the standardized rubric criteria.
     *
     * @param evaluationType optional evaluation type filter
     * @return 200 OK with rubric criteria
     */
    @GetMapping("/evaluation-rubrics")
    @PreAuthorize(AdmissionPerms.EVALUATION_RUBRICS_VIEW)
    public ResponseEntity<ApiResponse<List<RubricCriterionResponse>>> getRubric(
            @RequestParam(value = "evaluationType", required = false) EvaluationType evaluationType) {
        return ResponseEntity.ok(ApiResponse.success(evaluationService.getRubric(evaluationType)));
    }

    /**
     * Record an applicant evaluation scored against the standardized rubric (US-02003).
     *
     * @param request validated evaluation request
     * @return 201 Created with the evaluation result
     */
    @PostMapping("/evaluations")
    @PreAuthorize(AdmissionPerms.DOCUMENT_FOLLOW_UPS_MANAGE)
    public ResponseEntity<ApiResponse<EvaluationResponse>> recordEvaluation(
            @Valid @RequestBody RecordEvaluationRequest request) {
        log.info("Request to record {} evaluation for enquiry: {}", request.getEvaluationType(), request.getEnquiryId());
        EvaluationResponse response = evaluationService.recordEvaluation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Applicant evaluation recorded successfully", response));
    }

    /**
     * Retrieve an evaluation with its rubric scores.
     *
     * @param evaluationId the evaluation UUID
     * @return 200 OK with the evaluation
     */
    @GetMapping("/evaluations/{evaluationId}")
    @PreAuthorize(AdmissionPerms.EVALUATIONS_VIEW)
    public ResponseEntity<ApiResponse<EvaluationResponse>> getEvaluation(
            @PathVariable("evaluationId") UUID evaluationId) {
        log.info("Request to fetch evaluation: {}", evaluationId);
        return ResponseEntity.ok(ApiResponse.success(evaluationService.getEvaluation(evaluationId)));
    }

    /**
     * List all evaluations of an enquiry.
     *
     * @param enquiryId the enquiry UUID
     * @return 200 OK with evaluations
     */
    @GetMapping("/enquiries/{enquiryId}/evaluations")
    @PreAuthorize(AdmissionPerms.EVALUATIONS_VIEW)
    public ResponseEntity<ApiResponse<List<EvaluationResponse>>> listEvaluations(
            @PathVariable("enquiryId") UUID enquiryId) {
        log.info("Request to list evaluations for enquiry: {}", enquiryId);
        return ResponseEntity.ok(ApiResponse.success(evaluationService.listEvaluations(enquiryId)));
    }
}
