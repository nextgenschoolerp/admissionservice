package com.kvs.admission.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for the seat capacity and escalation rule of a grade (US-02014).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WaitlistRuleRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Grade ID is required")
    private String gradeId;

    @NotNull(message = "Total seats are required")
    @Min(value = 1, message = "Total seats must be at least 1")
    @Max(value = 1000, message = "Total seats must not exceed 1000")
    private Integer totalSeats;

    @NotNull(message = "Offer validity is required")
    @Min(value = 1, message = "Offer validity must be at least 1 day")
    @Max(value = 30, message = "Offer validity must not exceed 30 days")
    private Integer offerValidityDays;

    @NotNull(message = "Seat deposit amount is required")
    @DecimalMin(value = "0.01", message = "Seat deposit amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Seat deposit amount must have at most 8 digits and 2 decimals")
    private BigDecimal seatDepositAmount;

    private boolean autoEscalationEnabled;
}
