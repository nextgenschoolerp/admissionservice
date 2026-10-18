package com.kvs.admission.dto;

import com.kvs.admission.enums.CampaignStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Updates a campaign's cumulative spend and/or status (US-02019). Null fields are left unchanged.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCampaignRequest {

    /** Total spend to date (not an increment). */
    @DecimalMin(value = "0", message = "Spend must not be negative")
    @Digits(integer = 10, fraction = 2, message = "Spend must have at most 10 digits and 2 decimals")
    private BigDecimal spendAmount;

    private CampaignStatus status;
}
