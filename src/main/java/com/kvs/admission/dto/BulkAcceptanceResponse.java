package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of a bulk acceptance notification run (US-02018).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkAcceptanceResponse {
    private String gradeId;
    private UUID meritListId;
    /** Applicants with a live offer in scope. */
    private int poolSize;
    private int sent;
    /** Already notified for the same offer. */
    private int skipped;
    private int failed;
    private List<Item> notifications;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        private UUID notificationId;
        private UUID enquiryId;
        private String applicationNumber;
        private String offerCode;
        private BigDecimal amountDue;
        private LocalDate payBy;
        private String recipientPhone;
        private String status;
        private String providerMessageId;
        private String failureReason;
        private Instant dispatchedAt;
    }
}
