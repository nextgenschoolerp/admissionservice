package com.kvs.admission.entity;

import com.kvs.admission.enums.EducationBoard;
import com.kvs.admission.enums.TcVerificationResult;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Result of checking a transfer certificate against the issuing board's registry.
 * Covers: US-02017 — Verify transfer certificate validity against education board databases
 * Actor: Front-Office Counselor
 */
@Entity
@Table(name = "tc_verifications", indexes = {
        @Index(name = "idx_tc_verifications_enquiry", columnList = "enquiry_id, checked_at")
})
@Getter
@Setter
public class TcVerification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_id", nullable = false, unique = true, updatable = false)
    private UUID verificationId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    /** The TRANSFER_CERTIFICATE document in the vault that was checked. */
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board", nullable = false, length = 20)
    private EducationBoard board;

    @Column(name = "tc_number", nullable = false, length = 60)
    private String tcNumber;

    @Column(name = "previous_school_name", length = 200)
    private String previousSchoolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private TcVerificationResult result;

    @Column(name = "board_reference", length = 100)
    private String boardReference;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "response_message", length = 500)
    private String responseMessage;

    @Column(name = "checked_by", nullable = false, length = 150)
    private String checkedBy;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @PrePersist
    public void prePersist() {
        if (this.verificationId == null) {
            this.verificationId = UUID.randomUUID();
        }
    }
}
