package com.kvs.admission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for defining the sections of a grade (US-02013). Sections are created or updated;
 * sections not listed are left unchanged.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigureGradeSectionsRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Grade ID is required")
    private String gradeId;

    @NotEmpty(message = "At least one section is required")
    @Size(max = 26, message = "At most 26 sections per grade")
    private List<@Valid @NotNull SectionInput> sections;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SectionInput {

        @NotBlank(message = "Section code is required")
        @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Section code must be 1-10 upper-case letters or digits")
        private String sectionCode;

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 80, message = "Capacity must not exceed 80")
        private Integer capacity;

        @Size(max = 150, message = "Class teacher name must not exceed 150 characters")
        private String classTeacherName;
    }
}
