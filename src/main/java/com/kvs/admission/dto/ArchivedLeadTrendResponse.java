package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Monthly trend of archived (rejected / withdrawn) leads (US-02020).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArchivedLeadTrendResponse {
    private String tenantId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalArchived;
    private List<MonthBucket> months;
    /** Archived leads per referral source over the whole range. */
    private Map<String, Long> byReferralSource;
    /** Archived leads per target grade over the whole range. */
    private Map<String, Long> byGrade;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonthBucket {
        /** yyyy-MM, by the month the lead was closed. */
        private String month;
        private long rejected;
        private long withdrawn;
        /** Average entrance test score of rejected leads that sat the test; null when none did. */
        private BigDecimal averageRejectedEntranceScore;
    }
}
