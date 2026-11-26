package com.kvs.admission.controller;

import com.kvs.admission.service.ApplicantDocumentService;
import com.kvs.core.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The internal endpoint studentservice calls on enrolment: internal key required, verified documents only. */
class InternalApplicantDocumentControllerTest extends AdmissionControllerTestSupport {

    private static final String KEY = "schoolerp-dev-internal-key";
    private static final UUID DOC = UUID.fromString("5eed0002-0005-4000-8000-000000000001");
    private static final String URL = "/internal/v1/admissions/documents/" + DOC + "/storage?tenantId=tenant-x";

    @Test
    @DisplayName("Internal storage info: a call with the internal key gets the verified document's key; without it, 401")
    void storageInfo() throws Exception {
        when(documentService.storageInfo(eq("tenant-x"), eq(DOC))).thenReturn(new ApplicantDocumentService.StorageInfo(
                "tenant-x/admission-docs/enq/doc.pdf", "Birth.pdf", "application/pdf", 12L, "abc", "BIRTH_CERTIFICATE"));

        mockMvc.perform(get(URL).header("X-Internal-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageKey").value("tenant-x/admission-docs/enq/doc.pdf"))
                .andExpect(jsonPath("$.documentType").value("BIRTH_CERTIFICATE"));

        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("X-Internal-Key", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal storage info: a document that is not verified answers 409")
    void notVerified() throws Exception {
        when(documentService.storageInfo(anyString(), any(UUID.class)))
                .thenThrow(new ConflictException("Document is not verified, so it cannot be copied"));

        mockMvc.perform(get(URL).header("X-Internal-Key", KEY)).andExpect(status().isConflict());
    }
}
