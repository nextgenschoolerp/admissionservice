package com.kvs.admission.controller;

import com.kvs.admission.dto.ChannelConversionStat;
import com.kvs.admission.dto.ConversionRateReportResponse;
import com.kvs.core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AdmissionAnalyticsController}.
 * Covers: US-02007: Track lead conversion rates across marketing channels and referral sources
 * Actor: Marketing Manager
 */
class AdmissionAnalyticsControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/analytics/conversion-rates";

    @Test
    @DisplayName("GET conversion-rates - Marketing Manager gets per-channel conversion report (200)")
    void getConversionRates_Success() throws Exception {
        ConversionRateReportResponse report = ConversionRateReportResponse.builder()
                .tenantId("tenant-001")
                .totalLeads(10)
                .enrolledLeads(3)
                .overallConversionRate(new BigDecimal("30.00"))
                .topChannel("REFERRAL")
                .channels(List.of(ChannelConversionStat.builder()
                        .referralSource("REFERRAL").totalLeads(4).enrolledLeads(2)
                        .conversionRate(new BigDecimal("50.00")).build()))
                .build();
        when(analyticsService.getConversionRates(eq("tenant-001"), isNull(), eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 10, 1)))).thenReturn(report);

        mockMvc.perform(get(URL)
                        .param("tenantId", "tenant-001")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-10-01")
                        .header("Authorization", bearer("MARKETING_MANAGER"))
                        .header("X-Trace-Id", "test-trace-02007"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "test-trace-02007"))
                .andExpect(jsonPath("$.data.overallConversionRate").value(30.00))
                .andExpect(jsonPath("$.data.topChannel").value("REFERRAL"))
                .andExpect(jsonPath("$.data.channels[0].conversionRate").value(50.00));
    }

    @Test
    @DisplayName("GET conversion-rates - Missing tenantId returns 400")
    void getConversionRates_MissingTenant() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET conversion-rates - Malformed date returns 400")
    void getConversionRates_BadDate() throws Exception {
        mockMvc.perform(get(URL)
                        .param("tenantId", "tenant-001")
                        .param("fromDate", "01/01/2026")
                        .header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET conversion-rates - fromDate after toDate returns 400")
    void getConversionRates_InvertedRange() throws Exception {
        when(analyticsService.getConversionRates(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("fromDate must not be after toDate"));

        mockMvc.perform(get(URL)
                        .param("tenantId", "tenant-001")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-01-01")
                        .header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fromDate must not be after toDate"));
    }

    @Test
    @DisplayName("GET conversion-rates - No token returns 401")
    void getConversionRates_Unauthenticated() throws Exception {
        mockMvc.perform(get(URL).param("tenantId", "tenant-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET conversion-rates - Finance Cashier is forbidden (403)")
    void getConversionRates_Forbidden() throws Exception {
        mockMvc.perform(get(URL).param("tenantId", "tenant-001").header("Authorization", bearer("FINANCE_CASHIER")))
                .andExpect(status().isForbidden());
    }
}
