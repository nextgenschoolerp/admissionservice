package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for placing one student into a grade and section (US-02013).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaceStudentRequest {

    @NotBlank(message = "Grade ID is required")
    private String gradeId;

    @NotBlank(message = "Section code is required")
    @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Section code must be 1-10 upper-case letters or digits")
    private String sectionCode;
}
