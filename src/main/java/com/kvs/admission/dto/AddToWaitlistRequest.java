package com.kvs.admission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for adding an evaluated candidate to the waitlist of their grade (US-02014).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddToWaitlistRequest {

    @NotNull(message = "Enquiry ID is required")
    private UUID enquiryId;
}
