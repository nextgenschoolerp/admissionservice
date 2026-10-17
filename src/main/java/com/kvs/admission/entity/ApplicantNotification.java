package com.kvs.admission.entity;

import com.kvs.admission.enums.NotificationChannel;
import com.kvs.admission.enums.NotificationStatus;
import com.kvs.admission.enums.NotificationType;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound applicant communication log (one row per dispatch attempt).
 * Covers: US-02009 — Dispatch automated WhatsApp reminders for upcoming entrance evaluations
 * Actor: Front-Office Counselor
 */
@Entity
@Table(name = "applicant_notifications", indexes = {
        @Index(name = "idx_notifications_admit_card_type", columnList = "admit_card_id, notification_type, status"),
        @Index(name = "idx_notifications_enquiry", columnList = "enquiry_id")
})
@Getter
@Setter
public class ApplicantNotification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true, updatable = false)
    private UUID notificationId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "exam_id")
    private UUID examId;

    @Column(name = "admit_card_id")
    private UUID admitCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType notificationType;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "message_body", nullable = false, length = 1000)
    private String messageBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @PrePersist
    public void prePersist() {
        if (this.notificationId == null) {
            this.notificationId = UUID.randomUUID();
        }
    }
}
