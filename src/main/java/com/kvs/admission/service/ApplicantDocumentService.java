package com.kvs.admission.service;

import com.kvs.admission.dto.ApplicantDocumentResponse;
import com.kvs.admission.dto.VerifyDocumentRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.ApplicantDocumentRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.rbac.ScopeGuard;
import com.kvs.core.storage.FileResponses;
import com.kvs.core.storage.FileUploads;
import com.kvs.core.storage.StoredFile;
import com.kvs.core.storage.StoredFiles;
import com.kvs.core.storage.UploadPolicy;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for the applicant document vault.
 * Covers user story:
 * US-02004: Upload mandatory birth certificates and transfer records to the document vault
 * Actor: Entrance Exam Evaluator
 * <p>
 * Files are kept in the shared file store (ms-core {@link FileUploads}, area {@code admission-docs}).
 */
@Service
@Slf4j
public class ApplicantDocumentService {

    private static final Set<String> CLOSED_STATUSES = Set.of(
            EnquiryStatus.ENROLLED.name(),
            EnquiryStatus.REJECTED.name(),
            EnquiryStatus.WITHDRAWN.name());

    @Autowired
    private ApplicantDocumentRepository documentRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private FileUploads fileUploads;

    @Autowired
    private ScopeGuard scopeGuard;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Uploads a document into the vault. One document is kept per type; a PENDING or REJECTED
     * document of the same type is replaced, while a VERIFIED one cannot be overwritten.
     *
     * @param enquiryId    the enquiry the document belongs to
     * @param documentType the document type
     * @param file         the uploaded file (PDF, JPEG or PNG)
     * @return the stored document metadata
     */
    @Transactional
    public ApplicantDocumentResponse uploadDocument(UUID enquiryId, DocumentType documentType, MultipartFile file) {
        log.info("Uploading {} for enquiry: {}", documentType, enquiryId);
        AdmissionEnquiry enquiry = findEnquiry(enquiryId);

        if (CLOSED_STATUSES.contains(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("Documents cannot be uploaded for enquiry " + enquiry.getApplicationNumber()
                    + " in status " + enquiry.getEnquiryStatus());
        }

        ApplicantDocument document = documentRepository.findByEnquiryIdAndDocumentType(enquiryId, documentType)
                .orElse(null);
        if (document != null && document.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new ConflictException("A verified " + documentType + " already exists for enquiry "
                    + enquiry.getApplicationNumber());
        }
        String supersededKey = document != null ? document.getFilePath() : null;
        if (document == null) {
            document = new ApplicantDocument();
            document.setTenantId(enquiry.getTenantId());
            document.setEnquiryId(enquiryId);
            document.setDocumentType(documentType);
        }

        // FileUploads removes the new file again if this transaction rolls back
        StoredFile stored = fileUploads.save(enquiry.getTenantId(), UploadPolicy.ADMISSION_DOCUMENT, enquiryId, file);
        if (supersededKey != null) {
            // the replaced file goes once the new record is committed
            StoredFiles.deleteAfterCommit(fileUploads.store(), supersededKey);
        }

        document.setOriginalFileName(stored.fileName());
        document.setFilePath(stored.key());
        document.setContentType(stored.contentType());
        document.setFileSizeBytes(stored.sizeBytes());
        document.setChecksumSha256(stored.sha256());
        document.setVerificationStatus(DocumentVerificationStatus.PENDING);
        document.setUploadedBy(SecurityUtils.getCurrentUserEmail().orElse("entrance_exam_evaluator"));
        document.setVerifiedByUserId(null);
        document.setVerifiedAt(null);
        document.setVerificationNotes(null);
        ApplicantDocument saved = documentRepository.save(document);

        log.info("Stored {} {} for application {}", documentType, saved.getDocumentId(), enquiry.getApplicationNumber());
        auditService.logSuccess("applicant_document:" + enquiry.getTenantId(), "UPLOAD_APPLICANT_DOCUMENT",
                (supersededKey != null ? "Replaced " : "Uploaded ") + documentType
                        + " for application " + enquiry.getApplicationNumber()
                        + " | Document: " + saved.getDocumentId()
                        + " | Size: " + saved.getFileSizeBytes() + " bytes"
                        + " | SHA-256: " + saved.getChecksumSha256());

        return mapToResponse(saved, enquiry);
    }

    /**
     * One document's file, for viewing or for copying to the student profile on enrolment.
     * Sent with the shared download headers (no-store, nosniff); PDF and images show in the browser.
     *
     * @param enquiryId  the enquiry the document belongs to
     * @param documentId the document UUID
     * @return the file
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> documentFile(UUID enquiryId, UUID documentId) {
        ApplicantDocument document = loadDocument(documentId);
        if (!enquiryId.equals(document.getEnquiryId())) {
            throw new ResourceNotFoundException("ApplicantDocument", "documentId", documentId);
        }
        return FileResponses.inline(fileUploads.store(), document.getFilePath(), document.getContentType(),
                document.getOriginalFileName());
    }

    /**
     * Lists all vault documents of an enquiry.
     *
     * @param enquiryId the enquiry UUID
     * @return documents ordered by type
     */
    @Transactional(readOnly = true)
    public List<ApplicantDocumentResponse> listDocuments(UUID enquiryId) {
        log.info("Listing vault documents for enquiry: {}", enquiryId);
        AdmissionEnquiry enquiry = findEnquiry(enquiryId);
        return documentRepository.findByEnquiryIdOrderByDocumentTypeAsc(enquiryId).stream()
                .map(d -> mapToResponse(d, enquiry))
                .toList();
    }

    /**
     * Marks a PENDING document as VERIFIED or REJECTED. Rejections require notes.
     *
     * @param documentId the document UUID
     * @param request    the verification decision
     * @return the updated document
     */
    @Transactional
    public ApplicantDocumentResponse verifyDocument(UUID documentId, VerifyDocumentRequest request) {
        log.info("Verifying document {} as {}", documentId, request.getVerificationStatus());
        ApplicantDocument document = loadDocument(documentId);

        DocumentVerificationStatus decision = request.getVerificationStatus();
        if (decision == DocumentVerificationStatus.PENDING) {
            throw new BadRequestException("Verification status must be VERIFIED or REJECTED");
        }
        if (decision == DocumentVerificationStatus.REJECTED && !StringUtils.hasText(request.getVerificationNotes())) {
            throw new BadRequestException("Verification notes are required when rejecting a document");
        }
        if (document.getVerificationStatus() != DocumentVerificationStatus.PENDING) {
            throw new ConflictException("Document " + documentId + " has already been "
                    + document.getVerificationStatus());
        }

        document.setVerificationStatus(decision);
        document.setVerificationNotes(request.getVerificationNotes());
        document.setVerifiedByUserId(SecurityUtils.getCurrentUserEmail().orElse("registrar"));
        document.setVerifiedAt(Instant.now());
        ApplicantDocument saved = documentRepository.save(document);

        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(saved.getEnquiryId()).orElse(null);
        auditService.logSuccess("applicant_document:" + saved.getTenantId(), "VERIFY_APPLICANT_DOCUMENT",
                "Marked " + saved.getDocumentType() + " " + saved.getDocumentId() + " as " + decision
                        + (enquiry != null ? " | Application: " + enquiry.getApplicationNumber() : ""));

        return mapToResponse(saved, enquiry);
    }

    /**
     * Where a verified document's file is in the shared store, for studentservice to copy it on enrolment
     * (GET /internal/v1/admissions/documents/{id}/storage). Only a VERIFIED document is released.
     */
    @Transactional(readOnly = true)
    public StorageInfo storageInfo(String tenantId, UUID documentId) {
        ApplicantDocument document = documentRepository.findByDocumentId(documentId)
                .filter(d -> tenantId.equals(d.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("ApplicantDocument", "documentId", documentId));
        if (document.getVerificationStatus() != DocumentVerificationStatus.VERIFIED) {
            throw new ConflictException("Document " + documentId + " is not verified, so it cannot be copied");
        }
        return new StorageInfo(document.getFilePath(), document.getOriginalFileName(), document.getContentType(),
                document.getFileSizeBytes(), document.getChecksumSha256(), document.getDocumentType().name());
    }

    /** What the student profile needs to copy a verified document's file. */
    public record StorageInfo(String storageKey, String fileName, String contentType, long sizeBytes, String sha256,
                              String documentType) {
    }

    /** Loads a document; another school's document is reported as not found. */
    private ApplicantDocument loadDocument(UUID documentId) {
        ApplicantDocument document = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("ApplicantDocument", "documentId", documentId));
        scopeGuard.requireTenantRecord(document.getTenantId(), "ApplicantDocument", "documentId", documentId);
        return document;
    }

    private AdmissionEnquiry findEnquiry(UUID enquiryId) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        scopeGuard.requireTenantRecord(enquiry.getTenantId(), "AdmissionEnquiry", "enquiryId", enquiryId);
        return enquiry;
    }

    private ApplicantDocumentResponse mapToResponse(ApplicantDocument document, AdmissionEnquiry enquiry) {
        return ApplicantDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .tenantId(document.getTenantId())
                .enquiryId(document.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .documentType(document.getDocumentType().name())
                .mandatory(document.getDocumentType().isMandatory())
                .originalFileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .fileSizeBytes(document.getFileSizeBytes())
                .checksumSha256(document.getChecksumSha256())
                .verificationStatus(document.getVerificationStatus().name())
                .uploadedBy(document.getUploadedBy())
                .verifiedByUserId(document.getVerifiedByUserId())
                .verifiedAt(document.getVerifiedAt())
                .verificationNotes(document.getVerificationNotes())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
