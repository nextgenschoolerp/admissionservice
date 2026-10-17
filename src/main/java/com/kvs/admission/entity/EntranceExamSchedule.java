package com.kvs.admission.entity;

import com.kvs.admission.enums.ExamScheduleStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A scheduled entrance examination sitting for one grade.
 * Covers: US-02002 — Schedule entrance examination dates and auto-generate candidate admit cards
 * Actor: Admission Officer
 */
@Entity
@Table(name = "entrance_exam_schedules", indexes = {
        @Index(name = "idx_exam_schedules_tenant_branch_start", columnList = "tenant_id, branch_id, exam_start_at"),
        @Index(name = "idx_exam_schedules_status_start", columnList = "status, exam_start_at")
})
@Getter
@Setter
public class EntranceExamSchedule extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false, unique = true, updatable = false)
    private UUID examId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    @Column(name = "exam_code", nullable = false, unique = true, length = 50)
    private String examCode;

    @Column(name = "exam_title", nullable = false, length = 200)
    private String examTitle;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "target_grade_id", nullable = false, length = 100)
    private String targetGradeId;

    @Column(name = "exam_start_at", nullable = false)
    private Instant examStartAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "venue", nullable = false, length = 200)
    private String venue;

    @Column(name = "max_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "passing_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal passingMarks;

    @Column(name = "reporting_instructions", length = 1000)
    private String reportingInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamScheduleStatus status = ExamScheduleStatus.SCHEDULED;

    @PrePersist
    public void prePersist() {
        if (this.examId == null) {
            this.examId = UUID.randomUUID();
        }
    }
}
