package com.kvs.admission.controller;

import com.kvs.admission.dto.BulkAcceptanceRequest;
import com.kvs.admission.dto.BulkAcceptanceResponse;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AcceptanceNotificationController}.
 * Covers: US-02018 — Send bulk acceptance notifications to qualified applicant pools (Admission Officer)
 */
class AcceptanceNotificationControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/acceptance-notifications";

    @Test
    @DisplayName("POST - Admission Officer notifies the qualified pool (200)")
    void notify_Success() throws Exception {
        when(acceptanceService.notifyPool(any())).thenReturn(BulkAcceptanceResponse.builder().poolSize(4).sent(3).skipped(1).failed(0).build());

        mockMvc.perform(post(URL).header("Authorization", bearer("ADMISSION_OFFICER")).header("X-Trace-Id", "trace-02018")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", null))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-02018"))
                .andExpect(jsonPath("$.data.sent").value(3))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    @DisplayName("POST - Missing grade returns 400; unknown merit list returns 404")
    void notify_Errors() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer("ADMISSION_OFFICER")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", null, null))))
                .andExpect(status().isBadRequest());

        UUID unknown = UUID.randomUUID();
        when(acceptanceService.notifyPool(new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", unknown)))
                .thenThrow(new ResourceNotFoundException("MeritList", "meritListId", unknown));
        mockMvc.perform(post(URL).header("Authorization", bearer("ADMISSION_OFFICER")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", unknown))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST - Finance Cashier 403; no token 401")
    void notify_Security() throws Exception {
        String body = json(new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", null));
        mockMvc.perform(post(URL).header("Authorization", bearer("FINANCE_CASHIER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }
}
