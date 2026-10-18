package com.kvs.admission.controller;

import com.kvs.admission.dto.CampaignResponse;
import com.kvs.admission.dto.CostPerAcquisitionReportResponse;
import com.kvs.admission.dto.CreateCampaignRequest;
import com.kvs.admission.dto.UpdateCampaignRequest;
import com.kvs.admission.enums.CampaignStatus;
import com.kvs.admission.service.MarketingCampaignService;
import com.kvs.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for marketing campaigns and cost-per-acquisition analytics.
 * Covers user stories:
 * US-02019, US-02039, US-02059, US-02079, US-02099: Analyze cost-per-acquisition metrics across active marketing campaigns
 * Actor: Marketing Manager (US-02039/79). The Registrar (US-02019/59/99) is not granted.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/admissions")
public class MarketingCampaignController {

    @Autowired
    private MarketingCampaignService campaignService;

    @PostMapping("/marketing-campaigns")
    @PreAuthorize("hasAnyRole('MARKETING_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CampaignResponse>> create(@Valid @RequestBody CreateCampaignRequest request) {
        log.info("Request to create campaign {}", request.getCampaignCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Marketing campaign created", campaignService.createCampaign(request)));
    }

    @PatchMapping("/marketing-campaigns/{campaignId}")
    @PreAuthorize("hasAnyRole('MARKETING_MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CampaignResponse>> update(@PathVariable("campaignId") UUID campaignId,
                                                                @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Marketing campaign updated", campaignService.updateCampaign(campaignId, request)));
    }

    @GetMapping("/marketing-campaigns")
    @PreAuthorize("hasAnyRole('MARKETING_MANAGER', 'ADMISSION_OFFICER', 'FRONT_OFFICE_COUNSELOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> list(@RequestParam("tenantId") String tenantId,
                                                                    @RequestParam(value = "status", required = false) CampaignStatus status) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.listCampaigns(tenantId, status)));
    }

    /**
     * Cost per lead and per acquisition across campaigns (US-02019).
     */
    @GetMapping("/analytics/cost-per-acquisition")
    @PreAuthorize("hasAnyRole('MARKETING_MANAGER', 'ADMISSION_OFFICER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CostPerAcquisitionReportResponse>> costPerAcquisition(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(value = "status", required = false) CampaignStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Cost-per-acquisition report generated",
                campaignService.costPerAcquisition(tenantId, status)));
    }
}
