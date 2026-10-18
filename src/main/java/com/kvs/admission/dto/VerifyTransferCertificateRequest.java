package com.kvs.admission.dto;

import com.kvs.admission.enums.EducationBoard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for checking an uploaded transfer certificate with the issuing board (US-02017).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyTransferCertificateRequest {

    @NotNull(message = "Education board is required")
    private EducationBoard board;

    @NotBlank(message = "TC number is required")
    @Size(max = 60, message = "TC number must not exceed 60 characters")
    private String tcNumber;

    @Size(max = 200, message = "Previous school name must not exceed 200 characters")
    private String previousSchoolName;
}
