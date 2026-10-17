package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a recorded applicant evaluation.
 * US-02003: Evaluate applicant interview scores using standardized rubric criteria
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationResponse {
    private UUID evaluationId;
    private String tenantId;
    private UUID enquiryId;
    private String applicationNumber;
    private String evaluationType;
    private Instant evaluationDate;
    private String examinerId;
    private BigDecimal totalScore;
    private BigDecimal maxTotalScore;
    private BigDecimal passingScore;
    private String evaluationStatus;
    private String enquiryStatus;
    private String feedbackNotes;
    private List<RubricScoreResponse> rubricScores;
    private Instant createdAt;
}
