package com.kvs.admission.entity;

import com.kvs.admission.enums.Gender;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Active Student Master record created when a verified candidate lead is converted.
 * It is the admission-side hand-off record that the Student Information System (Module 03) consumes.
 * Covers: US-02005 — Convert verified candidate leads into active Student Master records
 * Actor: Applicant Parent
 */
@Entity
@Table(name = "student_master_records", indexes = {
        @Index(name = "idx_student_master_tenant_branch", columnList = "tenant_id, branch_id")
})
@Getter
@Setter
public class StudentMasterRecord extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, unique = true, updatable = false)
    private UUID studentId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "branch_id", nullable = false, length = 100)
    private String branchId;

    /** One student record per enquiry. */
    @Column(name = "enquiry_id", nullable = false, unique = true)
    private UUID enquiryId;

    @Column(name = "admission_number", nullable = false, unique = true, length = 50)
    private String admissionNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Column(name = "grade_id", nullable = false, length = 100)
    private String gradeId;

    @Column(name = "academic_year_id", nullable = false, length = 100)
    private String academicYearId;

    @Column(name = "guardian_name", nullable = false, length = 150)
    private String guardianName;

    @Column(name = "guardian_email", nullable = false, length = 150)
    private String guardianEmail;

    @Column(name = "guardian_phone", nullable = false, length = 20)
    private String guardianPhone;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "converted_by", nullable = false, length = 150)
    private String convertedBy;

    @PrePersist
    public void prePersist() {
        if (this.studentId == null) {
            this.studentId = UUID.randomUUID();
        }
    }
}
