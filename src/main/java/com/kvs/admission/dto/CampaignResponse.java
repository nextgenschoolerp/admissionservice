package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A marketing campaign, with its acquisition metrics when requested through the CPA report (US-02019).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CampaignResponse {
    private UUID campaignId;
    private String tenantId;
    private String campaignCode;
    private String campaignName;
    private String channel;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetAmount;
    private BigDecimal spendAmount;
    private String currency;
    private String status;
    private Instant createdAt;

    // Metrics (filled in the cost-per-acquisition report)
    private Long leads;
    private Long qualifiedLeads;
    private Long enrolledLeads;
    private BigDecimal conversionRate;
    private BigDecimal costPerLead;
    /** Spend divided by enrolled leads; null when nothing has converted yet. */
    private BigDecimal costPerAcquisition;
    private BigDecimal budgetUtilisation;
    private Boolean overBudget;
}
