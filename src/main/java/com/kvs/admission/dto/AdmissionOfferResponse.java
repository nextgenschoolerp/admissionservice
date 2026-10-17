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
 * Response DTO for an admission offer letter.
 * US-02006: Issue automated offer letters with embedded online fee payment links
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdmissionOfferResponse {
    private UUID offerId;
    private String tenantId;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String guardianName;
    private String guardianEmail;
    private String offerCode;
    private String offeredGradeId;
    private String academicYearId;
    private String offerStatus;
    private LocalDate validUntilDate;
    private BigDecimal initialFeeAmount;
    private String currency;
    private String paymentLinkUrl;
    private String letterContent;
    private Instant dispatchedAt;
    private Instant acceptedAt;
    private Instant createdAt;
}
