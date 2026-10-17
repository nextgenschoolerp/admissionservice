package com.kvs.admission.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for issuing an admission offer letter with a payment link.
 * US-02006: Issue automated offer letters with embedded online fee payment links
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IssueOfferRequest {

    @NotNull(message = "Enquiry ID is required")
    private UUID enquiryId;

    /** Defaults to the grade the candidate applied for. */
    @Size(max = 100, message = "Offered grade ID must not exceed 100 characters")
    private String offeredGradeId;

    @NotNull(message = "Offer validity date is required")
    @Future(message = "Offer validity date must be in the future")
    private LocalDate validUntilDate;

    @NotNull(message = "Initial fee amount is required")
    @DecimalMin(value = "0.01", message = "Initial fee amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Initial fee amount must have at most 8 digits and 2 decimals")
    private BigDecimal initialFeeAmount;
}
