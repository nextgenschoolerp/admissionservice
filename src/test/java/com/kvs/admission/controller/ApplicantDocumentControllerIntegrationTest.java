package com.kvs.admission.controller;

import com.kvs.admission.dto.ApplicantDocumentResponse;
import com.kvs.admission.dto.VerifyDocumentRequest;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ApplicantDocumentController}.
 * Covers: US-02004: Upload mandatory birth certificates and transfer records to the document vault
 * Actor: Entrance Exam Evaluator
 */
class ApplicantDocumentControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID ENQUIRY_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/admissions/enquiries/" + ENQUIRY_ID + "/documents";

    private final MockMultipartFile pdf = new MockMultipartFile("file", "birth_certificate.pdf",
            "application/pdf", "%PDF-1.7 test".getBytes());

    @Test
    @DisplayName("POST documents - Evaluator uploads birth certificate to the vault (201)")
    void uploadDocument_Success() throws Exception {
        ApplicantDocumentResponse response = ApplicantDocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .enquiryId(ENQUIRY_ID)
                .documentType("BIRTH_CERTIFICATE")
                .mandatory(true)
                .verificationStatus("PENDING")
                .fileSizeBytes(13L)
                .build();
        when(documentService.uploadDocument(eq(ENQUIRY_ID), eq(DocumentType.BIRTH_CERTIFICATE), any(MultipartFile.class)))
                .thenReturn(response);

        mockMvc.perform(multipart(URL).file(pdf)
                        .param("documentType", "BIRTH_CERTIFICATE")
                        .header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR"))
                        .header("X-Trace-Id", "test-trace-02004"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02004"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentType").value("BIRTH_CERTIFICATE"))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST documents - Missing file part returns 400")
    void uploadDocument_MissingFile() throws Exception {
        mockMvc.perform(multipart(URL)
                        .param("documentType", "BIRTH_CERTIFICATE")
                        .header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST documents - Invalid document type returns 400")
    void uploadDocument_InvalidType() throws Exception {
        mockMvc.perform(multipart(URL).file(pdf)
                        .param("documentType", "PASSPORT_PHOTO_OF_PET")
                        .header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST documents - No token returns 401")
    void uploadDocument_Unauthenticated() throws Exception {
        mockMvc.perform(multipart(URL).file(pdf).param("documentType", "BIRTH_CERTIFICATE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST documents - Applicant Parent is forbidden (403)")
    void uploadDocument_Forbidden() throws Exception {
        mockMvc.perform(multipart(URL).file(pdf)
                        .param("documentType", "BIRTH_CERTIFICATE")
                        .header("Authorization", bearer("APPLICANT_PARENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST documents - Unknown enquiry returns 404")
    void uploadDocument_NotFound() throws Exception {
        when(documentService.uploadDocument(eq(ENQUIRY_ID), any(DocumentType.class), any(MultipartFile.class)))
                .thenThrow(new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", ENQUIRY_ID));

        mockMvc.perform(multipart(URL).file(pdf)
                        .param("documentType", "TRANSFER_CERTIFICATE")
                        .header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH documents/{id}/verification - Registrar verifies a document (200)")
    void verifyDocument_Success() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentService.verifyDocument(eq(documentId), any(VerifyDocumentRequest.class)))
                .thenReturn(ApplicantDocumentResponse.builder().documentId(documentId).verificationStatus("VERIFIED").build());

        mockMvc.perform(patch("/api/v1/admissions/documents/" + documentId + "/verification")
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyDocumentRequest(DocumentVerificationStatus.VERIFIED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("PATCH documents/{id}/verification - Already verified document returns 409")
    void verifyDocument_Conflict() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentService.verifyDocument(eq(documentId), any(VerifyDocumentRequest.class)))
                .thenThrow(new ConflictException("Document has already been VERIFIED"));

        mockMvc.perform(patch("/api/v1/admissions/documents/" + documentId + "/verification")
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyDocumentRequest(DocumentVerificationStatus.VERIFIED, null))))
                .andExpect(status().isConflict());
    }
}
