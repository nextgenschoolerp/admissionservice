package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lead conversion report across marketing channels and referral sources.
 * US-02007: Track lead conversion rates across marketing channels and referral sources
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversionRateReportResponse {
    private String tenantId;
    private String branchId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalLeads;
    private long enrolledLeads;
    private BigDecimal overallConversionRate;
    private String topChannel;
    /** Channels ordered by conversion rate, highest first. */
    private List<ChannelConversionStat> channels;
    private Instant generatedAt;
}
