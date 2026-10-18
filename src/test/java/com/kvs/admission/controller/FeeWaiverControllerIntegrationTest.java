package com.kvs.admission.controller;

import com.kvs.admission.dto.FeeWaiverDecisionRequest;
import com.kvs.admission.dto.FeeWaiverResponse;
import com.kvs.admission.dto.RequestFeeWaiverRequest;
import com.kvs.admission.enums.ScholarshipCategory;
import com.kvs.admission.enums.WaiverStatus;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link FeeWaiverController}.
 * Covers: US-02016 — Process application fee waivers for scholarship candidates (School Principal)
 */
class FeeWaiverControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID OFFER = UUID.fromString("5eed0002-0006-4000-8000-000000000002");

    @Test
    @DisplayName("POST fee-waivers - Admission Officer requests a merit waiver (201)")
    void request_Success() throws Exception {
        when(feeWaiverService.requestWaiver(eq(OFFER), any())).thenReturn(FeeWaiverResponse.builder()
                .waiverId(UUID.randomUUID()).status("REQUESTED").waiverPercent(50)
                .waivedAmount(new BigDecimal("12500.00")).payableAmount(new BigDecimal("12500.00")).build());

        mockMvc.perform(post("/api/v1/admissions/offers/" + OFFER + "/fee-waivers").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RequestFeeWaiverRequest(ScholarshipCategory.MERIT, 50, "Top entrance test score (88)"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payableAmount").value(12500.00));
    }

    @Test
    @DisplayName("POST fee-waivers - Percent above 100 and blank justification return 400; cap breach returns 400")
    void request_Validation() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/offers/" + OFFER + "/fee-waivers").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new RequestFeeWaiverRequest(ScholarshipCategory.SIBLING, 120, " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));

        when(feeWaiverService.requestWaiver(eq(OFFER), any())).thenThrow(new BadRequestException("SIBLING waivers are capped at 25%"));
        mockMvc.perform(post("/api/v1/admissions/offers/" + OFFER + "/fee-waivers").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new RequestFeeWaiverRequest(ScholarshipCategory.SIBLING, 40, "Sibling in Grade 9"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SIBLING waivers are capped at 25%"));
    }

    @Test
    @DisplayName("PATCH decision - Only the principal decides; Evaluator and Admission Officer get 403")
    void decide_Roles() throws Exception {
        UUID waiver = UUID.randomUUID();
        when(feeWaiverService.decide(eq(waiver), any())).thenReturn(FeeWaiverResponse.builder().status("APPROVED").build());
        String body = json(new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, null));

        mockMvc.perform(patch("/api/v1/admissions/fee-waivers/" + waiver + "/decision").header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        for (String role : new String[]{"ENTRANCE_EXAM_EVALUATOR", "ADMISSION_OFFICER"}) {
            mockMvc.perform(patch("/api/v1/admissions/fee-waivers/" + waiver + "/decision").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("PATCH decision - Principal approving their own request gets 403 from the four-eyes rule")
    void decide_FourEyes() throws Exception {
        UUID waiver = UUID.randomUUID();
        when(feeWaiverService.decide(eq(waiver), any()))
                .thenThrow(new ForbiddenException("A waiver must be approved by someone other than the person who requested it"));

        mockMvc.perform(patch("/api/v1/admissions/fee-waivers/" + waiver + "/decision").header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
