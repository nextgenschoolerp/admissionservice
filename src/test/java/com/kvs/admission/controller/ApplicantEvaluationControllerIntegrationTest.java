package com.kvs.admission.controller;

import com.kvs.admission.dto.EvaluationResponse;
import com.kvs.admission.dto.RecordEvaluationRequest;
import com.kvs.admission.dto.RubricCriterionResponse;
import com.kvs.admission.dto.RubricScoreInput;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.enums.RubricCriterion;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ApplicantEvaluationController}.
 * Covers: US-02003: Evaluate applicant interview scores using standardized rubric criteria
 * Actor: Registrar
 */
class ApplicantEvaluationControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID ENQUIRY_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    private RecordEvaluationRequest interviewRequest() {
        List<RubricScoreInput> scores = RubricCriterion.forType(EvaluationType.INTERVIEW).stream()
                .map(c -> new RubricScoreInput(c, c.getMaxScore().subtract(BigDecimal.ONE), null))
                .toList();
        return new RecordEvaluationRequest(ENQUIRY_ID, EvaluationType.INTERVIEW, null, null,
                new BigDecimal("50"), scores, "Articulate and confident");
    }

    @Test
    @DisplayName("POST evaluations - Registrar records rubric interview evaluation (201)")
    void recordEvaluation_Success() throws Exception {
        EvaluationResponse response = EvaluationResponse.builder()
                .evaluationId(UUID.randomUUID())
                .enquiryId(ENQUIRY_ID)
                .evaluationType("INTERVIEW")
                .totalScore(new BigDecimal("95.00"))
                .maxTotalScore(new BigDecimal("100"))
                .evaluationStatus("PASSED")
                .enquiryStatus("EVALUATION_PASSED")
                .build();
        when(evaluationService.recordEvaluation(any(RecordEvaluationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .header("Authorization", bearer("REGISTRAR"))
                        .header("X-Trace-Id", "test-trace-02003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(interviewRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02003"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.totalScore").value(95.00));
    }

    @Test
    @DisplayName("POST evaluations - Missing rubric scores returns 400")
    void recordEvaluation_ValidationFailure() throws Exception {
        RecordEvaluationRequest request = interviewRequest();
        request.setRubricScores(List.of());

        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST evaluations - Unknown rubric criterion value returns 400, not 500")
    void recordEvaluation_UnknownCriterion() throws Exception {
        String body = "{\"enquiryId\":\"" + ENQUIRY_ID + "\",\"evaluationType\":\"INTERVIEW\",\"passingScore\":50,"
                + "\"rubricScores\":[{\"criterion\":\"NOT_A_CRITERION\",\"awardedScore\":5}]}";

        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("POST evaluations - Score above criterion maximum returns 400 from service")
    void recordEvaluation_ScoreAboveMaximum() throws Exception {
        when(evaluationService.recordEvaluation(any(RecordEvaluationRequest.class)))
                .thenThrow(new BadRequestException("Awarded score for COMMUNICATION exceeds the maximum of 20"));

        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(interviewRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST evaluations - No token returns 401")
    void recordEvaluation_Unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(interviewRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST evaluations - Marketing Manager is forbidden (403)")
    void recordEvaluation_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/evaluations")
                        .header("Authorization", bearer("MARKETING_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(interviewRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET evaluations/{id} - Unknown evaluation returns 404")
    void getEvaluation_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(evaluationService.getEvaluation(unknown))
                .thenThrow(new ResourceNotFoundException("EntranceEvaluation", "evaluationId", unknown));

        mockMvc.perform(get("/api/v1/admissions/evaluations/" + unknown).header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET evaluation-rubrics - Returns standardized interview rubric")
    void getRubric_Success() throws Exception {
        when(evaluationService.getRubric(eq(EvaluationType.INTERVIEW))).thenReturn(List.of(
                RubricCriterionResponse.builder().evaluationType("INTERVIEW").criterion("COMMUNICATION")
                        .criterionName("Communication Skills").maxScore(new BigDecimal("20")).build()));

        mockMvc.perform(get("/api/v1/admissions/evaluation-rubrics")
                        .param("evaluationType", "INTERVIEW")
                        .header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].criterion").value("COMMUNICATION"));
    }
}
