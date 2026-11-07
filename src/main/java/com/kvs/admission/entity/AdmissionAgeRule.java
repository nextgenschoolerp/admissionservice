package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * The minimum age for admission to a class under a board, counted on a cut-off date of the admission year.
 * Master rules (tenantId null, loaded from master/admission-age-rules.json) apply to every school of the board; a school's own
 * row (tenantId set, boardCode of its campus) replaces the master rule for that class.
 */
@Entity
@Table(name = "admission_age_rules", indexes = @Index(name = "idx_age_rule_tenant_board", columnList = "tenant_id, board_code"))
@Getter
@Setter
@NoArgsConstructor
public class AdmissionAgeRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id")
    private UUID ruleId;

    /** Null for a master rule; the school's id for its own override */
    @Column(name = "tenant_id")
    private String tenantId;

    /** education_boards.board_code; STATE on a master rule means every state board */
    @Column(name = "board_code", nullable = false, length = 40)
    private String boardCode;

    /** The class as the school names it: Nursery, LKG, UKG, Class 1 ... */
    @Column(name = "class_name", nullable = false, length = 50)
    private String className;

    @Column(name = "min_age_years", nullable = false)
    private int minAgeYears;

    @Column(name = "cutoff_month", nullable = false)
    private int cutoffMonth;

    @Column(name = "cutoff_day", nullable = false)
    private int cutoffDay;

    /** A starting rule to check against the current admission policy */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    public boolean isMaster() {
        return tenantId == null;
    }
}
