package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallbackResponse {
    private UUID callbackId;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String guardianName;
    private String guardianPhone;
    private Instant scheduledAt;
    private boolean overdue;
    private String assignedTo;
    private String reason;
    private String status;
    private Instant closedAt;
    private String closingNote;
}
