package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Lead funnel figures for one marketing channel / referral source (US-02007).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChannelConversionStat {
    private String referralSource;
    private long totalLeads;
    /** Leads that passed evaluation or progressed further (EVALUATION_PASSED, OFFER_ISSUED, ENROLLED). */
    private long qualifiedLeads;
    private long offersIssued;
    private long enrolledLeads;
    /** Leads that were rejected or withdrew. */
    private long lostLeads;
    /** Enrolled leads as a percentage of total leads. */
    private BigDecimal conversionRate;
}
