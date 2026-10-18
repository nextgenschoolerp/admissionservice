package com.kvs.admission.entity;

import com.kvs.admission.enums.CallDirection;
import com.kvs.admission.enums.CallOutcome;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notes of a telephone counseling call with an applicant family.
 * Covers: US-02015 — Log telephone counseling call notes and schedule automated callbacks
 */
@Entity
@Table(name = "counseling_calls", indexes = {
        @Index(name = "idx_counseling_calls_enquiry", columnList = "enquiry_id, called_at")
})
@Getter
@Setter
public class CounselingCall extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, unique = true, updatable = false)
    private UUID callId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private CallDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private CallOutcome outcome;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "called_at", nullable = false)
    private Instant calledAt;

    @Column(name = "counselor_id", nullable = false, length = 150)
    private String counselorId;

    @PrePersist
    public void prePersist() {
        if (this.callId == null) {
            this.callId = UUID.randomUUID();
        }
    }
}
