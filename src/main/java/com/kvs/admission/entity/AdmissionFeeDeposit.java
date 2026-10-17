package com.kvs.admission.entity;

import com.kvs.admission.enums.PaymentMode;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Seat reservation deposit collected against an admission offer, with its digital receipt number.
 * Covers: US-02008 — Collect seat reservation deposit fees and issue digital receipts
 * Actor: School Principal
 */
@Entity
@Table(name = "admission_fee_deposits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fee_deposits_offer", columnNames = {"offer_id"}),
        @UniqueConstraint(name = "uk_fee_deposits_tenant_txn_ref", columnNames = {"tenant_id", "transaction_reference"})
}, indexes = {
        @Index(name = "idx_fee_deposits_enquiry", columnList = "enquiry_id")
})
@Getter
@Setter
public class AdmissionFeeDeposit extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deposit_id", nullable = false, unique = true, updatable = false)
    private UUID depositId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "payment_timestamp", nullable = false)
    private Instant paymentTimestamp;

    @Column(name = "collected_by", nullable = false, length = 150)
    private String collectedBy;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @PrePersist
    public void prePersist() {
        if (this.depositId == null) {
            this.depositId = UUID.randomUUID();
        }
    }
}
