package com.kvs.admission.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for ranking candidates by weighted evaluation scores.
 * US-02010: Rank candidates automatically based on weighted entrance test scores
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateMeritListRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Target grade ID is required")
    private String targetGradeId;

    /** Weight of the entrance test score; entranceTestWeight + interviewWeight must equal 1. */
    @NotNull(message = "Entrance test weight is required")
    @DecimalMin(value = "0", message = "Entrance test weight must be between 0 and 1")
    @DecimalMax(value = "1", message = "Entrance test weight must be between 0 and 1")
    @Digits(integer = 1, fraction = 3, message = "Weights may have at most 3 decimals")
    private BigDecimal entranceTestWeight;

    @NotNull(message = "Interview weight is required")
    @DecimalMin(value = "0", message = "Interview weight must be between 0 and 1")
    @DecimalMax(value = "1", message = "Interview weight must be between 0 and 1")
    @Digits(integer = 1, fraction = 3, message = "Weights may have at most 3 decimals")
    private BigDecimal interviewWeight;
}
