package com.kvs.admission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A school's own admission age rule for a class of its board. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAgeRuleRequest {

    @NotNull(message = "tenantId is required")
    private String tenantId;

    @NotNull(message = "minAgeYears is required")
    @Min(value = 0, message = "minAgeYears must be between 0 and 25")
    @Max(value = 25, message = "minAgeYears must be between 0 and 25")
    private Integer minAgeYears;

    @NotNull(message = "cutoffMonth is required")
    @Min(value = 1, message = "cutoffMonth must be between 1 and 12")
    @Max(value = 12, message = "cutoffMonth must be between 1 and 12")
    private Integer cutoffMonth;

    @NotNull(message = "cutoffDay is required")
    @Min(value = 1, message = "cutoffDay must be between 1 and 31")
    @Max(value = 31, message = "cutoffDay must be between 1 and 31")
    private Integer cutoffDay;
}
