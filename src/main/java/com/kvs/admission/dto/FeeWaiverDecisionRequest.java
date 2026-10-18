package com.kvs.admission.dto;

import com.kvs.admission.enums.WaiverStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Principal's decision on a fee waiver (US-02016).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeeWaiverDecisionRequest {

    /** APPROVED or REJECTED. */
    @NotNull(message = "Decision is required")
    private WaiverStatus decision;

    /** Required when rejecting. */
    @Size(max = 1000, message = "Decision notes must not exceed 1000 characters")
    private String decisionNotes;
}
