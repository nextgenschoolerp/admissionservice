package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaitlistEntryResponse {
    private UUID entryId;
    /** 1-based queue position among WAITING entries; null for other statuses. */
    private Integer position;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private BigDecimal priorityScore;
    private String status;
    private UUID offerId;
    private Instant offeredAt;
    private String removalReason;
    private Instant addedAt;
}
