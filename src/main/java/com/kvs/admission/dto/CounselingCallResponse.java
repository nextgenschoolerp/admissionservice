package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A logged counseling call and the callback it scheduled, if any (US-02015).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CounselingCallResponse {
    private UUID callId;
    private UUID enquiryId;
    private String applicationNumber;
    private String direction;
    private String outcome;
    private Integer durationSeconds;
    private String notes;
    private Instant calledAt;
    private String counselorId;
    private String enquiryStatus;
    /** Present when this call scheduled a callback. */
    private CallbackResponse scheduledCallback;
    /** Explains why no callback was scheduled after an unanswered call (attempt limit reached). */
    private String callbackNote;
}
