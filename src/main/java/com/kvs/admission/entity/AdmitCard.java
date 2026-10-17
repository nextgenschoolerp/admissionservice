package com.kvs.admission.entity;

import com.kvs.admission.enums.AdmitCardStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Candidate admit card with roll number and seat for an entrance exam sitting.
 * Covers: US-02002 — auto-generate candidate admit cards
 */
@Entity
@Table(name = "admit_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admit_cards_exam_enquiry", columnNames = {"exam_id", "enquiry_id"}),
        @UniqueConstraint(name = "uk_admit_cards_exam_roll", columnNames = {"exam_id", "roll_number"})
}, indexes = {
        @Index(name = "idx_admit_cards_enquiry", columnList = "enquiry_id")
})
@Getter
@Setter
public class AdmitCard extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admit_card_id", nullable = false, unique = true, updatable = false)
    private UUID admitCardId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "admit_card_number", nullable = false, unique = true, length = 50)
    private String admitCardNumber;

    @Column(name = "roll_number", nullable = false, length = 60)
    private String rollNumber;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "candidate_name", nullable = false, length = 201)
    private String candidateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdmitCardStatus status = AdmitCardStatus.ISSUED;

    @PrePersist
    public void prePersist() {
        if (this.admitCardId == null) {
            this.admitCardId = UUID.randomUUID();
        }
    }
}
