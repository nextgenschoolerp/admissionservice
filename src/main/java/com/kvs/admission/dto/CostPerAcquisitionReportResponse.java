package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cost-per-acquisition report across marketing campaigns (US-02019).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CostPerAcquisitionReportResponse {
    private String tenantId;
    private String statusFilter;
    private int campaigns;
    private BigDecimal totalSpend;
    private long totalLeads;
    private long totalEnrolled;
    private BigDecimal blendedCostPerAcquisition;
    /** Campaign with the lowest cost per acquisition among those with at least one enrolment. */
    private String mostEfficientCampaign;
    /** Campaigns ordered by cost per acquisition (cheapest first; campaigns without enrolments last). */
    private List<CampaignResponse> campaignMetrics;
    private Instant generatedAt;
}
