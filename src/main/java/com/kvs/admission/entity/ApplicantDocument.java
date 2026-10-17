package com.kvs.admission.entity;

import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An applicant document stored in the document vault.
 * Covers: US-02004 — Upload mandatory birth certificates and transfer records to the document vault
 * Actor: Entrance Exam Evaluator
 */
@Entity
@Table(name = "applicant_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_applicant_documents_enquiry_type", columnNames = {"enquiry_id", "document_type"})
}, indexes = {
        @Index(name = "idx_applicant_docs_enquiry", columnList = "enquiry_id, verification_status")
})
@Getter
@Setter
public class ApplicantDocument extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true, updatable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Vault-relative path of the stored file. */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DocumentVerificationStatus verificationStatus = DocumentVerificationStatus.PENDING;

    @Column(name = "uploaded_by", length = 150)
    private String uploadedBy;

    @Column(name = "verified_by_user_id", length = 150)
    private String verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_notes", length = 1000)
    private String verificationNotes;

    @PrePersist
    public void prePersist() {
        if (this.documentId == null) {
            this.documentId = UUID.randomUUID();
        }
    }
}
