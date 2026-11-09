package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Whether a child meets the minimum age for a class on the cut-off date of the admission year. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgeCheckResponse {
    /** False when no rule exists for the class under this board (nothing to check) */
    private boolean ruleFound;
    /** Null when no rule exists */
    private Boolean eligible;
    private String className;
    private Integer minAgeYears;
    private LocalDate cutoffDate;
    private Integer ageYears;
    private Integer ageMonths;
    /** The latest date of birth that meets the rule */
    private LocalDate latestDateOfBirth;
    private String source;
    private String message;
}
