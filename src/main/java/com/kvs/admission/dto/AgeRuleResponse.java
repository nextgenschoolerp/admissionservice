package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The admission age rule in force for a class: the school's own, else the board's master rule. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgeRuleResponse {
    /** The board the rule was read for (the campus's board code) */
    private String boardCode;
    private String className;
    private int minAgeYears;
    private int cutoffMonth;
    private int cutoffDay;
    /** MASTER (the board's default) or SCHOOL (this school's own rule) */
    private String source;
    /** A starting rule to check against the current admission policy */
    private boolean needsReview;
}
