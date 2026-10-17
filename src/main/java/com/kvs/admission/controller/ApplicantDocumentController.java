package com.kvs.admission.controller;

import com.kvs.admission.dto.ApplicantDocumentResponse;
import com.kvs.admission.dto.VerifyDocumentRequest;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.service.ApplicantDocumentService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Controller for the applicant document vault.
 * Covers user story:
 * US-02004: Upload mandatory birth certificates and transfer records to the document vault
 * Actor: Entrance Exam Evaluator
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class ApplicantDocumentController {

    @Autowired
    private ApplicantDocumentService documentService;

    /**
     * Upload a document (birth certificate, transfer certificate, ...) to the vault (US-02004).
     *
     * @param enquiryId    the enquiry UUID
     * @param documentType the document type
     * @param file         PDF, JPEG or PNG file
     * @return 201 Created with stored document metadata
     */
    @PostMapping(value = "/enquiries/{enquiryId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ENTRANCE_EXAM_EVALUATOR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApplicantDocumentResponse>> uploadDocument(
            @PathVariable("enquiryId") UUID enquiryId,
            @RequestParam("documentType") DocumentType documentType,
            @RequestPart("file") MultipartFile file) {
        log.info("Request to upload {} for enquiry: {}", documentType, enquiryId);
        ApplicantDocumentResponse response = documentService.uploadDocument(enquiryId, documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded to the vault successfully", response));
    }

    /**
     * List the vault documents of an enquiry.
     *
     * @param enquiryId the enquiry UUID
     * @return 200 OK with documents
     */
    @GetMapping("/enquiries/{enquiryId}/documents")
    @PreAuthorize("hasAnyRole('ENTRANCE_EXAM_EVALUATOR', 'REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicantDocumentResponse>>> listDocuments(
            @PathVariable("enquiryId") UUID enquiryId) {
        log.info("Request to list vault documents for enquiry: {}", enquiryId);
        return ResponseEntity.ok(ApiResponse.success(documentService.listDocuments(enquiryId)));
    }

    /**
     * Verify or reject a vault document.
     *
     * @param documentId the document UUID
     * @param request    the verification decision
     * @return 200 OK with the updated document
     */
    @PatchMapping("/documents/{documentId}/verification")
    @PreAuthorize("hasAnyRole('REGISTRAR', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApplicantDocumentResponse>> verifyDocument(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody VerifyDocumentRequest request) {
        log.info("Request to mark document {} as {}", documentId, request.getVerificationStatus());
        ApplicantDocumentResponse response = documentService.verifyDocument(documentId, request);
        return ResponseEntity.ok(ApiResponse.success("Document verification updated successfully", response));
    }
}
