package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Seat capacity and escalation settings of a grade waitlist.
 * Covers: US-02014 — Manage applicant waitlist queues and automate seat escalation rules
 */
@Entity
@Table(name = "waitlist_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_waitlist_rules_scope", columnNames = {"tenant_id", "branch_id", "academic_year_id", "grade_id"})
})
@Getter
@Setter
public class WaitlistRule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "grade_id", nullable = false, length = 100)
    private String gradeId;

    /** Seats available in the grade for new admissions. */
    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    /** Days an escalated offer stays valid. */
    @Column(name = "offer_validity_days", nullable = false)
    private Integer offerValidityDays;

    @Column(name = "seat_deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal seatDepositAmount;

    /** When true the scheduler escalates this grade automatically. */
    @Column(name = "auto_escalation_enabled", nullable = false)
    private boolean autoEscalationEnabled;
}
