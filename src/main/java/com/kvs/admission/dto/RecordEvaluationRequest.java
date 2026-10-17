package com.kvs.admission.dto;

import com.kvs.admission.enums.EvaluationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for recording an applicant evaluation against the standardized rubric.
 * US-02003: Evaluate applicant interview scores using standardized rubric criteria
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordEvaluationRequest {

    @NotNull(message = "Enquiry ID is required")
    private UUID enquiryId;

    @NotNull(message = "Evaluation type is required")
    private EvaluationType evaluationType;

    /** Defaults to the current time when omitted. */
    @PastOrPresent(message = "Evaluation date must not be in the future")
    private Instant evaluationDate;

    /** Defaults to the authenticated user when omitted. */
    @Size(max = 150, message = "Examiner ID must not exceed 150 characters")
    private String examinerId;

    @NotNull(message = "Passing score is required")
    @DecimalMin(value = "0", message = "Passing score must be between 0 and 100")
    @DecimalMax(value = "100", message = "Passing score must be between 0 and 100")
    private BigDecimal passingScore;

    @NotEmpty(message = "Rubric scores are required")
    private List<@Valid @NotNull RubricScoreInput> rubricScores;

    @Size(max = 2000, message = "Feedback notes must not exceed 2000 characters")
    private String feedbackNotes;
}
