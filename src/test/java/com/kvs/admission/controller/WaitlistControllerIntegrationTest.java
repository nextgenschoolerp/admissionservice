package com.kvs.admission.controller;

import com.kvs.admission.dto.*;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link WaitlistController}.
 * Covers: US-02014 — Manage applicant waitlist queues and automate seat escalation rules (Admission Officer)
 */
class WaitlistControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String BASE = "/api/v1/admissions/waitlists";

    @Test
    @DisplayName("POST escalation - Admission Officer escalates free seats (200)")
    void escalate_Success() throws Exception {
        when(waitlistService.escalate(any())).thenReturn(WaitlistEscalationResponse.builder()
                .gradeId("GRADE-06").expiredOffers(1).freeSeatsBefore(2).offersIssued(2).stillWaiting(3).build());

        mockMvc.perform(post(BASE + "/escalation").header("Authorization", bearer("ADMISSION_OFFICER")).header("X-Trace-Id", "trace-02014")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WaitlistScopeRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-02014"))
                .andExpect(jsonPath("$.data.offersIssued").value(2))
                .andExpect(jsonPath("$.data.stillWaiting").value(3));
    }

    @Test
    @DisplayName("PUT rules - Zero seats and 60-day validity return 400")
    void saveRule_ValidationFailure() throws Exception {
        mockMvc.perform(put(BASE + "/rules").header("Authorization", bearer("ADMISSION_OFFICER")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WaitlistRuleRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", 0, 60,
                                new BigDecimal("25000"), false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    @DisplayName("POST entries - Not evaluated returns 400; already waitlisted returns 409")
    void add_Errors() throws Exception {
        UUID notEvaluated = UUID.randomUUID();
        UUID duplicate = UUID.randomUUID();
        when(waitlistService.addToWaitlist(new AddToWaitlistRequest(notEvaluated)))
                .thenThrow(new BadRequestException("Only candidates who passed evaluation can be waitlisted"));
        when(waitlistService.addToWaitlist(new AddToWaitlistRequest(duplicate)))
                .thenThrow(new ConflictException("Already on the waitlist"));

        mockMvc.perform(post(BASE + "/entries").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new AddToWaitlistRequest(notEvaluated))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/entries").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new AddToWaitlistRequest(duplicate))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET waitlist - No rule returns 404; Finance Cashier escalating returns 403")
    void get_NotFound_and_Forbidden() throws Exception {
        when(waitlistService.getWaitlist("tenant-001", "branch-001", "AY-2026-27", "GRADE-11"))
                .thenThrow(new ResourceNotFoundException("WaitlistRule", "grade", "GRADE-11"));

        mockMvc.perform(get(BASE).param("tenantId", "tenant-001").param("branchId", "branch-001").param("academicYearId", "AY-2026-27")
                        .param("gradeId", "GRADE-11").header("Authorization", bearer("ADMISSION_OFFICER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/escalation").header("Authorization", bearer("FINANCE_CASHIER")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new WaitlistScopeRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06"))))
                .andExpect(status().isForbidden());
    }
}
