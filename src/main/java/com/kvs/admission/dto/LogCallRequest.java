package com.kvs.admission.dto;

import com.kvs.admission.enums.CallDirection;
import com.kvs.admission.enums.CallOutcome;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for logging a counseling call (US-02015).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogCallRequest {

    @NotNull(message = "Call direction is required")
    private CallDirection direction;

    @NotNull(message = "Call outcome is required")
    private CallOutcome outcome;

    @NotNull(message = "Call duration is required")
    @Min(value = 0, message = "Call duration must not be negative")
    @Max(value = 7200, message = "Call duration must not exceed 2 hours")
    private Integer durationSeconds;

    @Size(max = 2000, message = "Call notes must not exceed 2000 characters")
    private String notes;

    /** Defaults to now. */
    @PastOrPresent(message = "Call time must not be in the future")
    private Instant calledAt;

    /** Callback time requested by the family; required for CALLBACK_REQUESTED, optional otherwise. */
    @Future(message = "Callback time must be in the future")
    private Instant callbackAt;
}
