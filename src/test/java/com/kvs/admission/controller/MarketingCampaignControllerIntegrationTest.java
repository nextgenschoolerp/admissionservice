package com.kvs.admission.controller;

import com.kvs.admission.dto.CampaignResponse;
import com.kvs.admission.dto.CostPerAcquisitionReportResponse;
import com.kvs.admission.dto.CreateCampaignRequest;
import com.kvs.core.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link MarketingCampaignController}.
 * Covers: US-02019 — Analyze cost-per-acquisition metrics across active marketing campaigns (Marketing Manager)
 */
class MarketingCampaignControllerIntegrationTest extends AdmissionControllerTestSupport {

    private CreateCampaignRequest campaign(String code, LocalDate start, LocalDate end) {
        return new CreateCampaignRequest("tenant-001", code, "Diwali Open House 2026", "SOCIAL_MEDIA", start, end, new BigDecimal("150000"));
    }

    @Test
    @DisplayName("POST marketing-campaigns - Marketing Manager creates a campaign (201); duplicate code 409")
    void create() throws Exception {
        when(campaignService.createCampaign(any())).thenReturn(CampaignResponse.builder().campaignCode("DIWALI-OPEN-HOUSE-26").status("ACTIVE").build());
        String body = json(campaign("DIWALI-OPEN-HOUSE-26", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 15)));

        mockMvc.perform(post("/api/v1/admissions/marketing-campaigns").header("Authorization", bearer("MARKETING_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        when(campaignService.createCampaign(any())).thenThrow(new ConflictException("Campaign code already exists"));
        mockMvc.perform(post("/api/v1/admissions/marketing-campaigns").header("Authorization", bearer("MARKETING_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST marketing-campaigns - Lower-case code returns 400; Registrar 403")
    void create_ValidationAndRole() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/marketing-campaigns").header("Authorization", bearer("MARKETING_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(campaign("diwali", LocalDate.now(), LocalDate.now()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/admissions/marketing-campaigns").header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(campaign("DIWALI-26", LocalDate.now(), LocalDate.now()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET cost-per-acquisition - Marketing Manager gets the CPA report (200); Registrar 403; no token 401")
    void costPerAcquisition() throws Exception {
        when(campaignService.costPerAcquisition(eq("tenant-001"), isNull())).thenReturn(CostPerAcquisitionReportResponse.builder()
                .campaigns(1).totalEnrolled(2).blendedCostPerAcquisition(new BigDecimal("42500.00")).mostEfficientCampaign("DIWALI-26")
                .campaignMetrics(List.of(CampaignResponse.builder().campaignCode("DIWALI-26").costPerAcquisition(new BigDecimal("42500.00")).build()))
                .build());

        mockMvc.perform(get("/api/v1/admissions/analytics/cost-per-acquisition").param("tenantId", "tenant-001")
                        .header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blendedCostPerAcquisition").value(42500.00))
                .andExpect(jsonPath("$.data.campaignMetrics[0].campaignCode").value("DIWALI-26"));
        mockMvc.perform(get("/api/v1/admissions/analytics/cost-per-acquisition").param("tenantId", "tenant-001")
                        .header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admissions/analytics/cost-per-acquisition").param("tenantId", "tenant-001"))
                .andExpect(status().isUnauthorized());
    }
}
