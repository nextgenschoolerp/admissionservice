package com.kvs.admission.controller;

import com.kvs.admission.dto.DocumentFollowUpResponse;
import com.kvs.admission.dto.DocumentFollowUpScanResponse;
import com.kvs.admission.dto.ResolveFollowUpRequest;
import com.kvs.admission.dto.ScanDocumentFollowUpsRequest;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link DocumentFollowUpController}.
 * Covers: US-02011 — Flag incomplete applicant document submissions for follow-up verification (Registrar)
 */
class DocumentFollowUpControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String SCAN = "/api/v1/admissions/document-follow-ups/scan";

    @Test
    @DisplayName("POST scan - Registrar flags incomplete document submissions (200)")
    void scan_Success() throws Exception {
        DocumentFollowUpResponse open = DocumentFollowUpResponse.builder()
                .followUpId(UUID.randomUUID()).applicationNumber("APP-2026-3F9A1C02")
                .outstandingDocuments(List.of("TRANSFER_CERTIFICATE")).status("OPEN").build();
        when(followUpService.scan(any(ScanDocumentFollowUpsRequest.class))).thenReturn(DocumentFollowUpScanResponse.builder()
                .applicantsScanned(5).newlyFlagged(1).openFollowUps(List.of(open)).build());

        mockMvc.perform(post(SCAN).header("Authorization", bearer("REGISTRAR")).header("X-Trace-Id", "trace-02011")
                        .contentType(MediaType.APPLICATION_JSON).content(json(new ScanDocumentFollowUpsRequest("tenant-001", "branch-001", 3))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-02011"))
                .andExpect(jsonPath("$.data.newlyFlagged").value(1))
                .andExpect(jsonPath("$.data.openFollowUps[0].outstandingDocuments[0]").value("TRANSFER_CERTIFICATE"));
    }

    @Test
    @DisplayName("POST scan - Follow-up window above 30 days returns 400")
    void scan_ValidationFailure() throws Exception {
        mockMvc.perform(post(SCAN).header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new ScanDocumentFollowUpsRequest("tenant-001", "branch-001", 45))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST scan - No token returns 401; Marketing Manager returns 403")
    void scan_Security() throws Exception {
        String body = json(new ScanDocumentFollowUpsRequest("tenant-001", "branch-001", 3));
        mockMvc.perform(post(SCAN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SCAN).header("Authorization", bearer("MARKETING_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH resolution - Already resolved returns 409; unknown returns 404")
    void resolve_Errors() throws Exception {
        UUID resolved = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(followUpService.resolve(eq(resolved), any())).thenThrow(new ConflictException("Follow-up is already RESOLVED"));
        when(followUpService.resolve(eq(unknown), any())).thenThrow(new ResourceNotFoundException("DocumentFollowUp", "followUpId", unknown));
        String body = json(new ResolveFollowUpRequest("Principal accepted a notarised copy"));

        mockMvc.perform(patch("/api/v1/admissions/document-follow-ups/" + resolved + "/resolution")
                        .header("Authorization", bearer("REGISTRAR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admissions/document-follow-ups/" + unknown + "/resolution")
                        .header("Authorization", bearer("REGISTRAR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET follow-ups - Invalid status filter returns 400")
    void list_InvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admissions/document-follow-ups").param("tenantId", "t").param("branchId", "b")
                        .param("status", "PENDING_FOREVER").header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isBadRequest());
    }
}
