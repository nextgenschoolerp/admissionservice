package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a document stored in the applicant document vault.
 * US-02004: Upload mandatory birth certificates and transfer records to the document vault
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicantDocumentResponse {
    private UUID documentId;
    private String tenantId;
    private UUID enquiryId;
    private String applicationNumber;
    private String documentType;
    private boolean mandatory;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;
    private String checksumSha256;
    private String verificationStatus;
    private String uploadedBy;
    private String verifiedByUserId;
    private Instant verifiedAt;
    private String verificationNotes;
    private Instant createdAt;
    private Instant updatedAt;
}
