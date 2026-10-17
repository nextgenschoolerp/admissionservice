package com.kvs.admission.dto;

import com.kvs.admission.enums.DocumentVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for verifying or rejecting a vault document.
 * US-02004: document authenticity check before enrollment
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyDocumentRequest {

    /** VERIFIED or REJECTED. */
    @NotNull(message = "Verification status is required")
    private DocumentVerificationStatus verificationStatus;

    /** Required when the document is rejected. */
    @Size(max = 1000, message = "Verification notes must not exceed 1000 characters")
    private String verificationNotes;
}
