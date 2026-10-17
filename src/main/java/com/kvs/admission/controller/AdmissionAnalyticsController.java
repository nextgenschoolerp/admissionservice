package com.kvs.admission.controller;

import com.kvs.admission.dto.ConversionRateReportResponse;
import com.kvs.admission.service.LeadConversionAnalyticsService;
import com.kvs.core.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controller for admission CRM analytics.
 * Covers user story:
 * US-02007: Track lead conversion rates across marketing channels and referral sources
 * Actor: Marketing Manager
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions/analytics")
public class AdmissionAnalyticsController {

    @Autowired
    private LeadConversionAnalyticsService analyticsService;

    /**
     * Lead conversion rates per marketing channel / referral source (US-02007).
     *
     * @param tenantId the tenant identifier
     * @param branchId optional branch filter
     * @param fromDate optional start date (ISO yyyy-MM-dd), defaults to 365 days before toDate
     * @param toDate   optional end date (ISO yyyy-MM-dd), defaults to today
     * @return 200 OK with the conversion report
     */
    @GetMapping("/conversion-rates")
    @PreAuthorize("hasAnyRole('MARKETING_MANAGER', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ConversionRateReportResponse>> getConversionRates(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.info("Request for lead conversion rates: tenant {}, branch {}, {} to {}", tenantId, branchId, fromDate, toDate);
        ConversionRateReportResponse response = analyticsService.getConversionRates(tenantId, branchId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Lead conversion report generated successfully", response));
    }
}
