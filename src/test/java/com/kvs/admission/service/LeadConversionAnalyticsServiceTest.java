package com.kvs.admission.service;

import com.kvs.admission.dto.ChannelConversionStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the funnel aggregation in {@link LeadConversionAnalyticsService} (US-02007).
 */
class LeadConversionAnalyticsServiceTest {

    @Test
    @DisplayName("Rows are folded into one funnel per channel and ordered by conversion rate")
    void aggregatesPerChannel() {
        List<Object[]> rows = List.of(
                new Object[]{"WALK_IN", "NEW_INQUIRY", 6L},
                new Object[]{"WALK_IN", "ENROLLED", 2L},
                new Object[]{"WALK_IN", "OFFER_ISSUED", 1L},
                new Object[]{"WALK_IN", "REJECTED", 1L},
                new Object[]{"referral", "ENROLLED", 3L},
                new Object[]{"REFERRAL", "EVALUATION_PASSED", 1L},
                new Object[]{null, "WITHDRAWN", 2L});

        List<ChannelConversionStat> stats = LeadConversionAnalyticsService.aggregate(rows);

        assertThat(stats).extracting(ChannelConversionStat::getReferralSource)
                .containsExactly("REFERRAL", "WALK_IN", "UNKNOWN");

        ChannelConversionStat referral = stats.get(0);
        assertThat(referral.getTotalLeads()).isEqualTo(4);
        assertThat(referral.getEnrolledLeads()).isEqualTo(3);
        assertThat(referral.getQualifiedLeads()).isEqualTo(4);
        assertThat(referral.getConversionRate()).isEqualByComparingTo("75.00");

        ChannelConversionStat walkIn = stats.get(1);
        assertThat(walkIn.getTotalLeads()).isEqualTo(10);
        assertThat(walkIn.getOffersIssued()).isEqualTo(3);
        assertThat(walkIn.getLostLeads()).isEqualTo(1);
        assertThat(walkIn.getConversionRate()).isEqualByComparingTo("20.00");

        ChannelConversionStat unknown = stats.get(2);
        assertThat(unknown.getLostLeads()).isEqualTo(2);
        assertThat(unknown.getConversionRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Rate is rounded half-up to two decimals and is zero when there are no leads")
    void rate() {
        assertThat(LeadConversionAnalyticsService.rate(1, 3)).isEqualByComparingTo("33.33");
        assertThat(LeadConversionAnalyticsService.rate(2, 3)).isEqualByComparingTo("66.67");
        assertThat(LeadConversionAnalyticsService.rate(0, 0)).isEqualByComparingTo("0.00");
    }
}
