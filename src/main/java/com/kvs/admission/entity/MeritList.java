package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A generated candidate merit list for one grade and academic year.
 * Covers: US-02010 — Rank candidates automatically based on weighted entrance test scores
 * Actor: Admission Officer
 */
@Entity
@Table(name = "merit_lists", indexes = {
        @Index(name = "idx_merit_lists_scope", columnList = "tenant_id, branch_id, academic_year_id, target_grade_id")
})
@Getter
@Setter
public class MeritList extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merit_list_id", nullable = false, unique = true, updatable = false)
    private UUID meritListId;

    @Column(name = "merit_list_code", nullable = false, unique = true, length = 50)
    private String meritListCode;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "target_grade_id", nullable = false, length = 100)
    private String targetGradeId;

    @Column(name = "entrance_test_weight", nullable = false, precision = 4, scale = 3)
    private BigDecimal entranceTestWeight;

    @Column(name = "interview_weight", nullable = false, precision = 4, scale = 3)
    private BigDecimal interviewWeight;

    @Column(name = "ranked_candidates", nullable = false)
    private Integer rankedCandidates;

    @Column(name = "excluded_candidates", nullable = false)
    private Integer excludedCandidates;

    @Column(name = "generated_by", nullable = false, length = 150)
    private String generatedBy;

    @OneToMany(mappedBy = "meritList", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("meritRank ASC, id ASC")
    private List<MeritListEntry> entries = new ArrayList<>();

    public void addEntry(MeritListEntry entry) {
        entry.setMeritList(this);
        this.entries.add(entry);
    }

    @PrePersist
    public void prePersist() {
        if (this.meritListId == null) {
            this.meritListId = UUID.randomUUID();
        }
    }
}
