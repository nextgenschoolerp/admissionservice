package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Stores admission inquiry / walk-in lead details in the CRM database.
 * Covers: US-02001 — Capture walk-in inquiry lead details directly into the CRM database
 * Actors: Front-Office Counselor
 */
@Entity
@Table(name = "admission_enquiries", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"application_number"})
}, indexes = {
        @Index(name = "idx_enquiries_tenant_branch_status", columnList = "tenant_id, branch_id, enquiry_status"),
        @Index(name = "idx_enquiries_phone_email", columnList = "guardian_phone, guardian_email")
})
@Getter
@Setter
public class AdmissionEnquiry extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enquiry_id", nullable = false, unique = true, updatable = false)
    private UUID enquiryId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "application_number", nullable = false, unique = true, length = 50)
    private String applicationNumber;

    @Column(name = "candidate_first_name", nullable = false, length = 100)
    private String candidateFirstName;

    @Column(name = "candidate_last_name", nullable = false, length = 100)
    private String candidateLastName;

    @Column(name = "target_grade_id", nullable = false, length = 100)
    private String targetGradeId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "guardian_name", nullable = false, length = 150)
    private String guardianName;

    @Column(name = "guardian_email", nullable = false, length = 150)
    private String guardianEmail;

    @Column(name = "guardian_phone", nullable = false, length = 20)
    private String guardianPhone;

    @Column(name = "referral_source", length = 50)
    private String referralSource = "WALK_IN";

    @Column(name = "enquiry_status", nullable = false, length = 30)
    private String enquiryStatus = "NEW_INQUIRY";

    @Column(name = "assigned_counselor_id", length = 100)
    private String assignedCounselorId;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    /** Marketing campaign that generated the lead (US-02019). */
    @Column(name = "campaign_code", length = 50)
    private String campaignCode;

    /** Set when a closed lead is moved to the archive (US-02020); archived leads leave the active pipeline. */
    @Column(name = "archived_at")
    private java.time.Instant archivedAt;

    @PrePersist
    public void prePersist() {
        if (this.enquiryId == null) {
            this.enquiryId = UUID.randomUUID();
        }
    }
}
