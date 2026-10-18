package com.kvs.admission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for closing a document follow-up manually (US-02011).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResolveFollowUpRequest {

    @NotBlank(message = "Resolution notes are required")
    @Size(max = 1000, message = "Resolution notes must not exceed 1000 characters")
    private String resolutionNotes;
}
