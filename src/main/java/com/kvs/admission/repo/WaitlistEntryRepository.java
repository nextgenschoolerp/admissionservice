package com.kvs.admission.repo;

import com.kvs.admission.entity.WaitlistEntry;
import com.kvs.admission.enums.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByEntryId(UUID entryId);

    boolean existsByEnquiryIdAndStatusIn(UUID enquiryId, Collection<WaitlistStatus> statuses);

    List<WaitlistEntry> findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatusIn(
            String tenantId, String branchId, String academicYearId, String gradeId, Collection<WaitlistStatus> statuses);

    List<WaitlistEntry> findByOfferIdIn(Collection<UUID> offerIds);
}
