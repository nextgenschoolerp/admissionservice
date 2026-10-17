package com.kvs.admission.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A ranked candidate within a merit list (US-02010).
 */
@Entity
@Table(name = "merit_list_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_merit_entries_list_enquiry", columnNames = {"merit_list_pk", "enquiry_id"})
})
@Getter
@Setter
public class MeritListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merit_list_pk", nullable = false)
    private MeritList meritList;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "application_number", nullable = false, length = 50)
    private String applicationNumber;

    @Column(name = "candidate_name", nullable = false, length = 201)
    private String candidateName;

    @Column(name = "entrance_test_score", precision = 5, scale = 2)
    private BigDecimal entranceTestScore;

    @Column(name = "interview_score", precision = 5, scale = 2)
    private BigDecimal interviewScore;

    @Column(name = "weighted_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightedScore;

    @Column(name = "merit_rank", nullable = false)
    private Integer meritRank;

    /** True when every evaluation of the candidate was PASSED. */
    @Column(name = "qualified", nullable = false)
    private boolean qualified;
}
