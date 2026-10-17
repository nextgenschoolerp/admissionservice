package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a generated merit list.
 * US-02010: Rank candidates automatically based on weighted entrance test scores
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeritListResponse {
    private UUID meritListId;
    private String meritListCode;
    private String tenantId;
    private String branchId;
    private String academicYearId;
    private String targetGradeId;
    private BigDecimal entranceTestWeight;
    private BigDecimal interviewWeight;
    private int rankedCandidates;
    /** Candidates skipped because a weighted evaluation is missing, or the lead was rejected/withdrawn. */
    private int excludedCandidates;
    private String generatedBy;
    private List<MeritListEntryResponse> entries;
    private Instant createdAt;
}
