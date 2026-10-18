package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for notifying a pool of qualified applicants of their admission offer (US-02018).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkAcceptanceRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Grade ID is required")
    private String gradeId;

    /** Optional: only notify candidates marked qualified in this merit list. */
    private UUID meritListId;
}
