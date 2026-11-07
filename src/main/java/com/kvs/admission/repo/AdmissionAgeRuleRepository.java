package com.kvs.admission.repo;

import com.kvs.admission.entity.AdmissionAgeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionAgeRuleRepository extends JpaRepository<AdmissionAgeRule, UUID> {

    /** The master rules and the school's own rows */
    @Query("select r from AdmissionAgeRule r where r.tenantId is null or r.tenantId = :tenantId order by r.className")
    List<AdmissionAgeRule> findVisible(@Param("tenantId") String tenantId);

    Optional<AdmissionAgeRule> findByTenantIdAndBoardCodeAndClassNameIgnoreCase(String tenantId, String boardCode, String className);

    boolean existsByTenantIdIsNullAndBoardCodeAndClassNameIgnoreCase(String boardCode, String className);
}
