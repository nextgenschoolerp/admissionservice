package com.kvs.admission.repo;

import com.kvs.admission.entity.StudentMasterRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentMasterRecordRepository extends JpaRepository<StudentMasterRecord, Long> {

    Optional<StudentMasterRecord> findByStudentId(UUID studentId);

    boolean existsByEnquiryId(UUID enquiryId);

    List<StudentMasterRecord> findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatus(
            String tenantId, String branchId, String academicYearId, String gradeId, String status);

    long countByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndSectionCode(
            String tenantId, String branchId, String academicYearId, String gradeId, String sectionCode);

    long countByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatus(
            String tenantId, String branchId, String academicYearId, String gradeId, String status);
}
