package com.kvs.admission.entity;

import com.kvs.admission.enums.CallbackStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A callback scheduled after a counseling call.
 * Covers: US-02015 — schedule automated callbacks
 */
@Entity
@Table(name = "callback_tasks", indexes = {
        @Index(name = "idx_callbacks_tenant_status_due", columnList = "tenant_id, status, scheduled_at"),
        @Index(name = "idx_callbacks_enquiry", columnList = "enquiry_id, status")
})
@Getter
@Setter
public class CallbackTask extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "callback_id", nullable = false, unique = true, updatable = false)
    private UUID callbackId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    /** Call that produced this callback. */
    @Column(name = "source_call_id", nullable = false)
    private UUID sourceCallId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "assigned_to", nullable = false, length = 150)
    private String assignedTo;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CallbackStatus status = CallbackStatus.SCHEDULED;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closing_note", length = 500)
    private String closingNote;

    @PrePersist
    public void prePersist() {
        if (this.callbackId == null) {
            this.callbackId = UUID.randomUUID();
        }
    }
}
