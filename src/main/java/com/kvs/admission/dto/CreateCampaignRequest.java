package com.kvs.admission.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for registering a recruitment marketing campaign (US-02019).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCampaignRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Campaign code is required")
    @Pattern(regexp = "^[A-Z0-9-]{3,50}$", message = "Campaign code must be 3-50 upper-case letters, digits or hyphens")
    private String campaignCode;

    @NotBlank(message = "Campaign name is required")
    @Size(max = 200, message = "Campaign name must not exceed 200 characters")
    private String campaignName;

    /** Marketing channel, aligned with enquiry referral sources (e.g. SOCIAL_MEDIA, WEBSITE, NEWSPAPER). */
    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "^[A-Z_]{2,50}$", message = "Channel must be upper-case letters and underscores")
    private String channel;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Budget is required")
    @DecimalMin(value = "1", message = "Budget must be at least 1")
    @Digits(integer = 10, fraction = 2, message = "Budget must have at most 10 digits and 2 decimals")
    private BigDecimal budgetAmount;
}
