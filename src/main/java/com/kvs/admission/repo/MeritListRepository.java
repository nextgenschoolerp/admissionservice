package com.kvs.admission.repo;

import com.kvs.admission.entity.MeritList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeritListRepository extends JpaRepository<MeritList, Long> {

    @EntityGraph(attributePaths = "entries")
    Optional<MeritList> findByMeritListId(UUID meritListId);

    /** Most recent merit list of a grade, used to prioritise the waitlist (US-02014). */
    @EntityGraph(attributePaths = "entries")
    Optional<MeritList> findFirstByTenantIdAndBranchIdAndAcademicYearIdAndTargetGradeIdOrderByCreatedAtDesc(
            String tenantId, String branchId, String academicYearId, String targetGradeId);
}
