package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeritListEntryResponse {
    private int meritRank;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private BigDecimal entranceTestScore;
    private BigDecimal interviewScore;
    private BigDecimal weightedScore;
    private boolean qualified;
}
