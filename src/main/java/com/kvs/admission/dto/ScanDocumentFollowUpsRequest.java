package com.kvs.admission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for scanning a branch for incomplete document submissions.
 * US-02011: Flag incomplete applicant document submissions for follow-up verification
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanDocumentFollowUpsRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    /** Days allowed for the family to complete the documents (default 3). */
    @Min(value = 1, message = "Follow-up window must be at least 1 day")
    @Max(value = 30, message = "Follow-up window must not exceed 30 days")
    private Integer followUpDays;
}
