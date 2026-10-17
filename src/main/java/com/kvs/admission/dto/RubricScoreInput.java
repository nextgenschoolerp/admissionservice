package com.kvs.admission.dto;

import com.kvs.admission.enums.RubricCriterion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Score for one rubric criterion within an evaluation request.
 * US-02003: Evaluate applicant interview scores using standardized rubric criteria
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RubricScoreInput {

    @NotNull(message = "Rubric criterion is required")
    private RubricCriterion criterion;

    @NotNull(message = "Awarded score is required")
    @DecimalMin(value = "0", message = "Awarded score must not be negative")
    @Digits(integer = 3, fraction = 2, message = "Awarded score must have at most 3 digits and 2 decimals")
    private BigDecimal awardedScore;

    @Size(max = 500, message = "Criterion remarks must not exceed 500 characters")
    private String remarks;
}
