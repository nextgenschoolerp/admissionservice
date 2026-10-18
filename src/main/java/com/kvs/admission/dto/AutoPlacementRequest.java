package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for placing every unplaced enrolled student of a grade into sections (US-02013).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutoPlacementRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Grade ID is required")
    private String gradeId;
}
