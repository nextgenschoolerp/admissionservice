package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores system audit log entries for all privileged operations in admission service.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_ip_address", columnList = "ip_address"),
        @Index(name = "idx_audit_event_time", columnList = "event_time")
})
@Getter
@Setter
public class AuditLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user who performed the action */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** IP address from which the action was performed */
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** The action/event that was performed (e.g., CAPTURE_WALKIN_LEAD) */
    @Column(name = "action", nullable = false, length = 200)
    private String action;

    /** Resource or module affected by the action */
    @Column(name = "resource", length = 200)
    private String resource;

    /** Outcome of the action: SUCCESS or FAILURE */
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    /** Timestamp when the event occurred */
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    /** Additional context or details about the event */
    @Column(name = "details", length = 1000)
    private String details;
}
