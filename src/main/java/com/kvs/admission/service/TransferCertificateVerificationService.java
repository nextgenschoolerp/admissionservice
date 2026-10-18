package com.kvs.admission.service;

import com.kvs.admission.dto.TcVerificationResponse;
import com.kvs.admission.dto.VerifyTransferCertificateRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.entity.TcVerification;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.admission.enums.TcVerificationResult;
import com.kvs.admission.notification.EducationBoardGateway;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.ApplicantDocumentRepository;
import com.kvs.admission.repo.TcVerificationRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service checking uploaded transfer certificates against the issuing board's registry.
 * Covers user stories:
 * US-02017, US-02057, US-02097: Verify transfer certificate validity against education board databases
 * Actor: Front-Office Counselor (Applicant Parent in US-02037/77 is not granted: the check updates vault verification status)
 */
@Service
@Slf4j
public class TransferCertificateVerificationService {

    @Autowired
    private TcVerificationRepository verificationRepository;

    @Autowired
    private ApplicantDocumentRepository documentRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private EducationBoardGateway boardGateway;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Looks the certificate up with the board. VALID verifies the vault document, INVALID rejects it,
     * NOT_FOUND leaves it pending for a manual check. Every lookup is recorded.
     */
    @Transactional
    public TcVerificationResponse verify(UUID enquiryId, VerifyTransferCertificateRequest request) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        ApplicantDocument tc = documentRepository.findByEnquiryIdAndDocumentType(enquiryId, DocumentType.TRANSFER_CERTIFICATE)
                .orElseThrow(() -> new BadRequestException("Upload the transfer certificate for " + enquiry.getApplicationNumber()
                        + " to the document vault before verifying it"));
        if (tc.getVerificationStatus() == DocumentVerificationStatus.VERIFIED) {
            throw new ConflictException("The transfer certificate of " + enquiry.getApplicationNumber() + " is already verified");
        }

        String candidate = enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName();
        EducationBoardGateway.BoardLookupResult lookup = boardGateway.verifyTransferCertificate(
                request.getBoard(), request.getTcNumber().trim().toUpperCase(), candidate, request.getPreviousSchoolName());
        String checker = SecurityUtils.getCurrentUserEmail().orElse("front_office_counselor");
        Instant now = Instant.now();

        TcVerification verification = new TcVerification();
        verification.setTenantId(enquiry.getTenantId());
        verification.setEnquiryId(enquiryId);
        verification.setDocumentId(tc.getDocumentId());
        verification.setBoard(request.getBoard());
        verification.setTcNumber(request.getTcNumber().trim().toUpperCase());
        verification.setPreviousSchoolName(request.getPreviousSchoolName());
        verification.setResult(lookup.result());
        verification.setBoardReference(lookup.boardReference());
        verification.setIssueDate(lookup.issueDate());
        verification.setResponseMessage(lookup.message());
        verification.setCheckedBy(checker);
        verification.setCheckedAt(now);
        TcVerification saved = verificationRepository.save(verification);

        if (lookup.result() != TcVerificationResult.NOT_FOUND) {
            boolean valid = lookup.result() == TcVerificationResult.VALID;
            tc.setVerificationStatus(valid ? DocumentVerificationStatus.VERIFIED : DocumentVerificationStatus.REJECTED);
            tc.setVerifiedByUserId(checker);
            tc.setVerifiedAt(now);
            tc.setVerificationNotes(valid
                    ? "Verified with the " + request.getBoard() + " registry, reference " + lookup.boardReference()
                    : "Board check failed: " + lookup.message());
            documentRepository.save(tc);
        }

        auditService.logSuccess("tc_verification:" + enquiry.getTenantId(), "VERIFY_TRANSFER_CERTIFICATE",
                request.getBoard() + " lookup of TC " + saved.getTcNumber() + " for " + enquiry.getApplicationNumber()
                        + " | Result: " + lookup.result() + (lookup.boardReference() != null ? " | Ref: " + lookup.boardReference() : ""));
        return map(saved, enquiry, tc.getVerificationStatus());
    }

    /**
     * All board lookups made for an enquiry, newest first.
     */
    @Transactional(readOnly = true)
    public List<TcVerificationResponse> history(UUID enquiryId) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        DocumentVerificationStatus current = documentRepository.findByEnquiryIdAndDocumentType(enquiryId, DocumentType.TRANSFER_CERTIFICATE)
                .map(ApplicantDocument::getVerificationStatus).orElse(null);
        return verificationRepository.findByEnquiryIdOrderByCheckedAtDesc(enquiryId).stream()
                .map(v -> map(v, enquiry, current)).toList();
    }

    private TcVerificationResponse map(TcVerification v, AdmissionEnquiry enquiry, DocumentVerificationStatus documentStatus) {
        return TcVerificationResponse.builder()
                .verificationId(v.getVerificationId())
                .enquiryId(v.getEnquiryId())
                .applicationNumber(enquiry.getApplicationNumber())
                .documentId(v.getDocumentId())
                .board(v.getBoard().name())
                .tcNumber(v.getTcNumber())
                .previousSchoolName(v.getPreviousSchoolName())
                .result(v.getResult().name())
                .boardReference(v.getBoardReference())
                .issueDate(v.getIssueDate())
                .responseMessage(v.getResponseMessage())
                .documentVerificationStatus(documentStatus != null ? documentStatus.name() : null)
                .checkedBy(v.getCheckedBy())
                .checkedAt(v.getCheckedAt())
                .build();
    }
}
