package com.kvs.admission.controller;

import com.kvs.admission.dto.DispatchRemindersRequest;
import com.kvs.admission.dto.ReminderDispatchSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ApplicantReminderController}.
 * Covers: US-02009: Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 * Actor: Front-Office Counselor
 */
class ApplicantReminderControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/reminders/entrance-evaluations";

    @Test
    @DisplayName("POST reminders - Counselor dispatches WhatsApp reminders (200)")
    void dispatchReminders_Success() throws Exception {
        ReminderDispatchSummaryResponse summary = ReminderDispatchSummaryResponse.builder()
                .tenantId("tenant-001").branchId("branch-001")
                .examsCovered(1).remindersSent(2).remindersSkipped(1).remindersFailed(0)
                .notifications(List.of())
                .build();
        when(reminderService.dispatchEntranceEvaluationReminders(eq("tenant-001"), eq("branch-001"), eq(24)))
                .thenReturn(summary);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .header("X-Trace-Id", "test-trace-02009")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DispatchRemindersRequest("tenant-001", "branch-001", 24))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "test-trace-02009"))
                .andExpect(jsonPath("$.data.remindersSent").value(2))
                .andExpect(jsonPath("$.data.remindersSkipped").value(1));
    }

    @Test
    @DisplayName("POST reminders - Lookahead above 168 hours returns 400")
    void dispatchReminders_ValidationFailure() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DispatchRemindersRequest("tenant-001", "branch-001", 500))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST reminders - Missing body returns 400")
    void dispatchReminders_MissingBody() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("POST reminders - No token returns 401")
    void dispatchReminders_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DispatchRemindersRequest("tenant-001", "branch-001", 24))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST reminders - Marketing Manager is forbidden (403)")
    void dispatchReminders_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("MARKETING_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DispatchRemindersRequest("tenant-001", "branch-001", 24))))
                .andExpect(status().isForbidden());
    }
}
