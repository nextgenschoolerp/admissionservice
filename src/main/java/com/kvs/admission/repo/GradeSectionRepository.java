package com.kvs.admission.repo;

import com.kvs.admission.entity.GradeSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeSectionRepository extends JpaRepository<GradeSection, Long> {

    List<GradeSection> findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdOrderBySectionCodeAsc(
            String tenantId, String branchId, String academicYearId, String gradeId);

    Optional<GradeSection> findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndSectionCode(
            String tenantId, String branchId, String academicYearId, String gradeId, String sectionCode);
}
