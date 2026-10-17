package com.kvs.admission.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for scheduling an entrance examination and generating admit cards.
 * US-02002: Schedule entrance examination dates and auto-generate candidate admit cards
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleEntranceExamRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Exam title is required")
    @Size(max = 200, message = "Exam title must not exceed 200 characters")
    private String examTitle;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Target grade ID is required")
    private String targetGradeId;

    @NotNull(message = "Exam start date-time is required")
    @Future(message = "Exam start date-time must be in the future")
    private Instant examStartAt;

    @NotNull(message = "Duration is required")
    @Min(value = 15, message = "Duration must be at least 15 minutes")
    @Max(value = 480, message = "Duration must not exceed 480 minutes")
    private Integer durationMinutes;

    @NotBlank(message = "Venue is required")
    @Size(max = 200, message = "Venue must not exceed 200 characters")
    private String venue;

    @NotNull(message = "Maximum marks are required")
    @DecimalMin(value = "1", message = "Maximum marks must be at least 1")
    @Digits(integer = 4, fraction = 2, message = "Maximum marks must have at most 4 digits and 2 decimals")
    private BigDecimal maxMarks;

    @NotNull(message = "Passing marks are required")
    @DecimalMin(value = "0", message = "Passing marks must not be negative")
    @Digits(integer = 4, fraction = 2, message = "Passing marks must have at most 4 digits and 2 decimals")
    private BigDecimal passingMarks;

    @Size(max = 1000, message = "Reporting instructions must not exceed 1000 characters")
    private String reportingInstructions;

    @NotEmpty(message = "At least one candidate enquiry ID is required")
    @Size(max = 500, message = "At most 500 candidates can be scheduled per exam")
    private List<@NotNull(message = "Enquiry ID must not be null") UUID> enquiryIds;
}
