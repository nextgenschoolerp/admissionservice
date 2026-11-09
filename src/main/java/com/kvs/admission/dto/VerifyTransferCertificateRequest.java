package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    /** The issuing board: a code of the education boards master list (CBSE, ICSE, ISC, STATE_MH, IB, ...). Optional: CBSE when not given. */
    @Pattern(regexp = "^[A-Za-z_]{2,40}$", message = "Education board must be a board code such as CBSE or STATE_MH")
    private String board;

    @NotBlank(message = "TC number is required")
    @Size(max = 60, message = "TC number must not exceed 60 characters")
    private String tcNumber;

    @Size(max = 200, message = "Previous school name must not exceed 200 characters")
    private String previousSchoolName;
}
