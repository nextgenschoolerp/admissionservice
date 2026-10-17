package com.kvs.admission.entity;

import com.kvs.admission.enums.RubricCriterion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Score awarded against a single standardized rubric criterion.
 * Covers: US-02003 — standardized rubric criteria
 */
@Entity
@Table(name = "evaluation_rubric_scores", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rubric_scores_evaluation_criterion", columnNames = {"evaluation_pk", "criterion"})
})
@Getter
@Setter
public class EvaluationRubricScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_pk", nullable = false)
    private EntranceEvaluation evaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion", nullable = false, length = 40)
    private RubricCriterion criterion;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "awarded_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal awardedScore;

    @Column(name = "remarks", length = 500)
    private String remarks;
}
