package com.kvs.admission.dto;

import com.kvs.admission.enums.ScholarshipCategory;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for a scholarship fee waiver on an offer (US-02016).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestFeeWaiverRequest {

    @NotNull(message = "Scholarship category is required")
    private ScholarshipCategory scholarshipCategory;

    @NotNull(message = "Waiver percent is required")
    @Min(value = 5, message = "Waiver percent must be at least 5")
    @Max(value = 100, message = "Waiver percent must not exceed 100")
    private Integer waiverPercent;

    @NotBlank(message = "Justification is required")
    @Size(max = 1000, message = "Justification must not exceed 1000 characters")
    private String justification;
}
