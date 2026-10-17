package com.kvs.admission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for dispatching WhatsApp reminders for upcoming entrance evaluations.
 * US-02009: Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DispatchRemindersRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    /** Remind candidates of exams starting within this many hours (default 48). */
    @Min(value = 1, message = "Lookahead must be at least 1 hour")
    @Max(value = 168, message = "Lookahead must not exceed 168 hours")
    private Integer lookaheadHours;
}
