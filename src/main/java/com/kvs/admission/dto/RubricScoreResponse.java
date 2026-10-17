package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RubricScoreResponse {
    private String criterion;
    private String criterionName;
    private BigDecimal maxScore;
    private BigDecimal awardedScore;
    private String remarks;
}
