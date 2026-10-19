package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Closes an open application: "Not offered" (REJECTED) or WITHDRAWN by the family.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryDecisionRequest {

    @NotBlank(message = "decision is required")
    @Pattern(regexp = "^(REJECTED|WITHDRAWN)$", message = "decision must be REJECTED or WITHDRAWN")
    private String decision;

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;
}
