package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a generated candidate admit card.
 * US-02002: auto-generate candidate admit cards
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdmitCardResponse {
    private UUID admitCardId;
    private String admitCardNumber;
    private UUID examId;
    private String examCode;
    private String examTitle;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String rollNumber;
    private String seatNumber;
    private Instant examStartAt;
    private Integer durationMinutes;
    private String venue;
    private String reportingInstructions;
    private String status;
}
