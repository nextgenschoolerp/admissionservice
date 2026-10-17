package com.kvs.admission.enums;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Standardized rubric criteria for each evaluation type (US-02003).
 * The maximum scores of every evaluation type add up to 100, so the awarded total is a percentage.
 */
public enum RubricCriterion {
    MATHEMATICS(EvaluationType.ENTRANCE_TEST, "Mathematics", 40),
    LANGUAGE(EvaluationType.ENTRANCE_TEST, "Language Proficiency", 30),
    LOGICAL_REASONING(EvaluationType.ENTRANCE_TEST, "Logical Reasoning", 30),

    COMMUNICATION(EvaluationType.INTERVIEW, "Communication Skills", 20),
    ACADEMIC_APTITUDE(EvaluationType.INTERVIEW, "Academic Aptitude", 30),
    GENERAL_AWARENESS(EvaluationType.INTERVIEW, "General Awareness", 20),
    CONFIDENCE_AND_CONDUCT(EvaluationType.INTERVIEW, "Confidence & Conduct", 15),
    PARENT_INTERACTION(EvaluationType.INTERVIEW, "Parent Interaction", 15);

    private final EvaluationType evaluationType;
    private final String displayName;
    private final BigDecimal maxScore;

    RubricCriterion(EvaluationType evaluationType, String displayName, int maxScore) {
        this.evaluationType = evaluationType;
        this.displayName = displayName;
        this.maxScore = BigDecimal.valueOf(maxScore);
    }

    public EvaluationType getEvaluationType() {
        return evaluationType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public static List<RubricCriterion> forType(EvaluationType type) {
        return Arrays.stream(values())
                .filter(c -> c.evaluationType == type)
                .toList();
    }
}
