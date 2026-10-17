package com.kvs.admission.dto;

import com.kvs.admission.enums.Gender;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for converting a verified lead into an active Student Master record.
 * US-02005: Convert verified candidate leads into active Student Master records
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConvertLeadRequest {

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;
}
