package com.kvs.admission.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A class section of a grade with its seat capacity, used to place confirmed admissions.
 * Covers: US-02013 — Assign target grade levels and sections to confirmed admission leads
 */
@Entity
@Table(name = "grade_sections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_grade_sections_scope_code",
                columnNames = {"tenant_id", "branch_id", "academic_year_id", "grade_id", "section_code"})
})
@Getter
@Setter
public class GradeSection extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false, unique = true, updatable = false)
    private UUID sectionId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "grade_id", nullable = false, length = 100)
    private String gradeId;

    @Column(name = "section_code", nullable = false, length = 10)
    private String sectionCode;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "class_teacher_name", length = 150)
    private String classTeacherName;

    @PrePersist
    public void prePersist() {
        if (this.sectionId == null) {
            this.sectionId = UUID.randomUUID();
        }
    }
}
