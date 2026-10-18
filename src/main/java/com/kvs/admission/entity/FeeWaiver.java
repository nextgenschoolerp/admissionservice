package com.kvs.admission.entity;

import com.kvs.admission.enums.ScholarshipCategory;
import com.kvs.admission.enums.WaiverStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A scholarship waiver on the seat reservation fee of an admission offer.
 * Covers: US-02016 — Process application fee waivers for scholarship candidates
 * Actor: School Principal (approver)
 */
@Entity
@Table(name = "fee_waivers", indexes = {
        @Index(name = "idx_fee_waivers_offer_status", columnList = "offer_id, status"),
        @Index(name = "idx_fee_waivers_tenant_status", columnList = "tenant_id, status")
})
@Getter
@Setter
public class FeeWaiver extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waiver_id", nullable = false, unique = true, updatable = false)
    private UUID waiverId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scholarship_category", nullable = false, length = 40)
    private ScholarshipCategory scholarshipCategory;

    @Column(name = "waiver_percent", nullable = false)
    private Integer waiverPercent;

    @Column(name = "original_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "waived_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal waivedAmount;

    @Column(name = "payable_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal payableAmount;

    @Column(name = "justification", nullable = false, length = 1000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaiverStatus status = WaiverStatus.REQUESTED;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", length = 1000)
    private String decisionNotes;

    /** Receipt issued automatically when a 100% waiver is approved. */
    @Column(name = "waiver_receipt_number", length = 50)
    private String waiverReceiptNumber;

    @PrePersist
    public void prePersist() {
        if (this.waiverId == null) {
            this.waiverId = UUID.randomUUID();
        }
    }
}
