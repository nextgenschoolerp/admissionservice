package com.kvs.admission.entity;

import com.kvs.admission.enums.OfferStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Admission offer letter with an embedded online fee payment link.
 * Covers: US-02006 — Issue automated offer letters with embedded online fee payment links
 * Actor: Finance Cashier
 */
@Entity
@Table(name = "admission_offers", indexes = {
        @Index(name = "idx_admission_offers_enquiry_status", columnList = "enquiry_id, offer_status")
})
@Getter
@Setter
public class AdmissionOffer extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false, unique = true, updatable = false)
    private UUID offerId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "offer_code", nullable = false, unique = true, length = 50)
    private String offerCode;

    @Column(name = "offered_grade_id", nullable = false, length = 100)
    private String offeredGradeId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_status", nullable = false, length = 20)
    private OfferStatus offerStatus = OfferStatus.DRAFT;

    @Column(name = "valid_until_date", nullable = false)
    private LocalDate validUntilDate;

    @Column(name = "initial_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialFeeAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    /** Unguessable token identifying the offer on the payment page. */
    @Column(name = "payment_link_token", nullable = false, unique = true, length = 64)
    private String paymentLinkToken;

    @Column(name = "payment_link_url", nullable = false, length = 500)
    private String paymentLinkUrl;

    @Column(name = "letter_content", nullable = false, length = 4000)
    private String letterContent;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @PrePersist
    public void prePersist() {
        if (this.offerId == null) {
            this.offerId = UUID.randomUUID();
        }
    }
}
