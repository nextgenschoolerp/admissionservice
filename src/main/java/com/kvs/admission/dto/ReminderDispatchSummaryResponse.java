package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a WhatsApp reminder dispatch run.
 * US-02009: Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReminderDispatchSummaryResponse {
    private String tenantId;
    private String branchId;
    private Instant windowStart;
    private Instant windowEnd;
    private int examsCovered;
    private int remindersSent;
    /** Candidates already reminded for the same exam. */
    private int remindersSkipped;
    private int remindersFailed;
    private List<ReminderNotificationResponse> notifications;
}
