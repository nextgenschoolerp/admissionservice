package com.kvs.admission.controller;

import com.kvs.admission.dto.CallbackResponse;
import com.kvs.admission.dto.CounselingCallResponse;
import com.kvs.admission.dto.LogCallRequest;
import com.kvs.admission.enums.CallDirection;
import com.kvs.admission.enums.CallOutcome;
import com.kvs.core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link CounselingCallController}.
 * Covers: US-02015 — Log telephone counseling call notes and schedule automated callbacks
 */
class CounselingCallControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID ENQUIRY = UUID.fromString("5eed0002-0001-4000-8000-000000000009");
    private static final String CALLS = "/api/v1/admissions/enquiries/" + ENQUIRY + "/calls";

    @Test
    @DisplayName("POST calls - Counselor logs an unanswered call and gets an automatic callback (201)")
    void logCall_Success() throws Exception {
        when(callService.logCall(eq(ENQUIRY), any())).thenReturn(CounselingCallResponse.builder()
                .callId(UUID.randomUUID()).outcome("NO_ANSWER")
                .scheduledCallback(CallbackResponse.builder().reason("Retry after no answer (attempt 1 of 3)").status("SCHEDULED").build())
                .build());

        mockMvc.perform(post(CALLS).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LogCallRequest(CallDirection.OUTBOUND, CallOutcome.NO_ANSWER, 0, "Rang twice", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledCallback.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("POST calls - Callback time in the past and negative duration return 400")
    void logCall_ValidationFailure() throws Exception {
        mockMvc.perform(post(CALLS).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LogCallRequest(CallDirection.OUTBOUND, CallOutcome.CALLBACK_REQUESTED, -5, null, null,
                                Instant.now().minus(1, ChronoUnit.HOURS)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    @DisplayName("POST calls - Callback requested without a time returns 400 from the service")
    void logCall_MissingCallbackTime() throws Exception {
        when(callService.logCall(eq(ENQUIRY), any())).thenThrow(new BadRequestException("callbackAt is required"));

        mockMvc.perform(post(CALLS).header("Authorization", bearer("ADMISSION_OFFICER")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LogCallRequest(CallDirection.INBOUND, CallOutcome.CALLBACK_REQUESTED, 60, null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Marketing Manager may read call history but not log calls; Registrar may do neither")
    void roles() throws Exception {
        when(callService.listCalls(ENQUIRY)).thenReturn(List.of());
        String body = json(new LogCallRequest(CallDirection.OUTBOUND, CallOutcome.CONNECTED, 120, null, null, null));

        mockMvc.perform(get(CALLS).header("Authorization", bearer("MARKETING_MANAGER"))).andExpect(status().isOk());
        mockMvc.perform(post(CALLS).header("Authorization", bearer("MARKETING_MANAGER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CALLS).header("Authorization", bearer("REGISTRAR"))).andExpect(status().isForbidden());
        mockMvc.perform(get(CALLS)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET callbacks - Malformed dueBefore returns 400")
    void dueCallbacks_BadTimestamp() throws Exception {
        mockMvc.perform(get("/api/v1/admissions/callbacks").param("tenantId", "t").param("dueBefore", "tomorrow")
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")))
                .andExpect(status().isBadRequest());
    }
}
