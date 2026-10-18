package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Denormalized copy of a closed lead taken when it is archived, kept for historical trend reporting.
 * Covers: US-02020 — Archive rejected inquiry leads for historical trend reporting
 * Actor: School Principal
 */
@Entity
@Table(name = "archived_lead_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_archived_leads_enquiry", columnNames = {"enquiry_id"})
}, indexes = {
        @Index(name = "idx_archived_leads_tenant_closed", columnList = "tenant_id, closed_at"),
        @Index(name = "idx_archived_leads_batch", columnList = "archive_batch_id")
})
@Getter
@Setter
public class ArchivedLeadSnapshot extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_batch_id", nullable = false)
    private UUID archiveBatchId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "application_number", nullable = false, length = 50)
    private String applicationNumber;

    /** REJECTED or WITHDRAWN. */
    @Column(name = "final_status", nullable = false, length = 30)
    private String finalStatus;

    @Column(name = "referral_source", length = 50)
    private String referralSource;

    @Column(name = "campaign_code", length = 50)
    private String campaignCode;

    @Column(name = "target_grade_id", nullable = false, length = 100)
    private String targetGradeId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "enquiry_created_at")
    private Instant enquiryCreatedAt;

    /** When the lead reached its final status (last update of the enquiry). */
    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "entrance_test_score", precision = 5, scale = 2)
    private BigDecimal entranceTestScore;

    @Column(name = "closure_remarks", length = 2000)
    private String closureRemarks;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @Column(name = "archived_by", nullable = false, length = 150)
    private String archivedBy;
}
