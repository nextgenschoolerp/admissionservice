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
public class FeeWaiverResponse {
    private UUID waiverId;
    private String tenantId;
    private UUID offerId;
    private String offerCode;
    private String offerStatus;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String scholarshipCategory;
    private Integer waiverPercent;
    private BigDecimal originalAmount;
    private BigDecimal waivedAmount;
    private BigDecimal payableAmount;
    private String justification;
    private String status;
    private String requestedBy;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionNotes;
    private String waiverReceiptNumber;
    private Instant createdAt;
}
