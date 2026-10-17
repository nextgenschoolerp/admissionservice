package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Digital receipt for a collected seat reservation deposit.
 * US-02008: Collect seat reservation deposit fees and issue digital receipts
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepositReceiptResponse {
    private UUID depositId;
    private String receiptNumber;
    private String tenantId;
    private UUID offerId;
    private String offerCode;
    private String offerStatus;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String guardianName;
    private String offeredGradeId;
    private String academicYearId;
    private BigDecimal amountPaid;
    private String currency;
    private String paymentMode;
    private String transactionReference;
    private Instant paymentTimestamp;
    private String collectedBy;
    private String remarks;
}
