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
 * Response DTO for a scheduled entrance exam with its admit cards.
 * US-02002: Schedule entrance examination dates and auto-generate candidate admit cards
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntranceExamResponse {
    private Long id;
    private UUID examId;
    private String tenantId;
    private String branchId;
    private String examCode;
    private String examTitle;
    private String academicYearId;
    private String targetGradeId;
    private Instant examStartAt;
    private Integer durationMinutes;
    private String venue;
    private BigDecimal maxMarks;
    private BigDecimal passingMarks;
    private String reportingInstructions;
    private String status;
    private int admitCardCount;
    private List<AdmitCardResponse> admitCards;
    private Instant createdAt;
}
