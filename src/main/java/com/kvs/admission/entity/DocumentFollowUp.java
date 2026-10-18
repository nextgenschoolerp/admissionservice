package com.kvs.admission.entity;

import com.kvs.admission.enums.FollowUpStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Follow-up flag raised for an applicant whose mandatory documents are incomplete.
 * Covers: US-02011 — Flag incomplete applicant document submissions for follow-up verification
 * Actor: Registrar
 */
@Entity
@Table(name = "document_follow_ups", indexes = {
        @Index(name = "idx_doc_followups_tenant_status", columnList = "tenant_id, branch_id, status"),
        @Index(name = "idx_doc_followups_enquiry", columnList = "enquiry_id, status")
})
@Getter
@Setter
public class DocumentFollowUp extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follow_up_id", nullable = false, unique = true, updatable = false)
    private UUID followUpId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    /** Comma-separated mandatory document types that still need action, e.g. "TRANSFER_CERTIFICATE". */
    @Column(name = "outstanding_documents", nullable = false, length = 300)
    private String outstandingDocuments;

    /** Human-readable issue per document, e.g. "TRANSFER_CERTIFICATE: rejected - not signed". */
    @Column(name = "issue_summary", nullable = false, length = 1000)
    private String issueSummary;

    @Column(name = "follow_up_due_date", nullable = false)
    private LocalDate followUpDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FollowUpStatus status = FollowUpStatus.OPEN;

    @Column(name = "flagged_by", nullable = false, length = 150)
    private String flaggedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @PrePersist
    public void prePersist() {
        if (this.followUpId == null) {
            this.followUpId = UUID.randomUUID();
        }
    }
}
