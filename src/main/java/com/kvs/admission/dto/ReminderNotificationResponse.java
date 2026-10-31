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
public class ReminderNotificationResponse {
    private UUID notificationId;
    private UUID enquiryId;
    private String applicationNumber;
    private UUID examId;
    private String examCode;
    private String rollNumber;
    private String channel;
    private String recipientPhone;
    private String status;
    private String providerMessageId;
    private String failureReason;
    private Instant dispatchedAt;
    /** email copy to the guardian: SENT, FAILED (with emailDetail), or null when there is no guardian email */
    private String emailStatus;
    private String emailDetail;
}
