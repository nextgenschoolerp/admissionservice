package com.kvs.admission.entity;

import com.kvs.admission.enums.WaitlistStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A candidate waiting for a seat in a grade.
 * Covers: US-02014 — Manage applicant waitlist queues and automate seat escalation rules
 */
@Entity
@Table(name = "waitlist_entries", indexes = {
        @Index(name = "idx_waitlist_scope_status", columnList = "tenant_id, branch_id, academic_year_id, grade_id, status"),
        @Index(name = "idx_waitlist_enquiry", columnList = "enquiry_id")
})
@Getter
@Setter
public class WaitlistEntry extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, unique = true, updatable = false)
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "grade_id", nullable = false, length = 100)
    private String gradeId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    /** Merit-list weighted score (or entrance test score) used to order the queue. */
    @Column(name = "priority_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @PrePersist
    public void prePersist() {
        if (this.entryId == null) {
            this.entryId = UUID.randomUUID();
        }
    }
}
