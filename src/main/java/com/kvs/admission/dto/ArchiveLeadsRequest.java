package com.kvs.admission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for archiving closed leads (US-02020).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArchiveLeadsRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    /** Optional: restrict to one branch. */
    private String branchId;

    /** Also archive WITHDRAWN leads (REJECTED are always included). Defaults to true. */
    private Boolean includeWithdrawn;

    /** Only archive leads closed at least this many days ago (default 30). */
    @Min(value = 0, message = "Minimum age must not be negative")
    @Max(value = 3650, message = "Minimum age must not exceed 3650 days")
    private Integer minimumDaysClosed;
}
