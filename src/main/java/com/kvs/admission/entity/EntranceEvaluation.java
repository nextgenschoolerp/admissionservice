package com.kvs.admission.entity;

import com.kvs.admission.enums.EvaluationStatus;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scored evaluation (entrance test or interview) of an applicant against a standardized rubric.
 * Covers: US-02003 — Evaluate applicant interview scores using standardized rubric criteria
 * Actor: Registrar
 */
@Entity
@Table(name = "entrance_evaluations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_evaluations_enquiry_type", columnNames = {"enquiry_id", "evaluation_type"})
}, indexes = {
        @Index(name = "idx_evaluations_enquiry", columnList = "enquiry_id")
})
@Getter
@Setter
public class EntranceEvaluation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false, unique = true, updatable = false)
    private UUID evaluationId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 20)
    private EvaluationType evaluationType;

    @Column(name = "evaluation_date", nullable = false)
    private Instant evaluationDate;

    @Column(name = "examiner_id", nullable = false, length = 150)
    private String examinerId;

    /** Sum of awarded rubric scores; every rubric totals 100 so this is a percentage. */
    @Column(name = "total_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "passing_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal passingScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 20)
    private EvaluationStatus evaluationStatus;

    @Column(name = "feedback_notes", length = 2000)
    private String feedbackNotes;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<EvaluationRubricScore> rubricScores = new ArrayList<>();

    public void addRubricScore(EvaluationRubricScore score) {
        score.setEvaluation(this);
        this.rubricScores.add(score);
    }

    @PrePersist
    public void prePersist() {
        if (this.evaluationId == null) {
            this.evaluationId = UUID.randomUUID();
        }
    }
}
