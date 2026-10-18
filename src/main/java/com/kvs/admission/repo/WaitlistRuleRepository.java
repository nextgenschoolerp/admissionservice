package com.kvs.admission.repo;

import com.kvs.admission.entity.WaitlistRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRuleRepository extends JpaRepository<WaitlistRule, Long> {

    Optional<WaitlistRule> findByTenantIdAndBranchIdAndAcademicYearIdAndGradeId(String tenantId, String branchId,
                                                                               String academicYearId, String gradeId);

    List<WaitlistRule> findByAutoEscalationEnabledTrue();
}
