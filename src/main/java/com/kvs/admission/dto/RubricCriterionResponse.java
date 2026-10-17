package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One criterion of the standardized evaluation rubric (US-02003).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricCriterionResponse {
    private String evaluationType;
    private String criterion;
    private String criterionName;
    private BigDecimal maxScore;
}
