package com.kvs.admission.repo;

import com.kvs.admission.entity.DocumentFollowUp;
import com.kvs.admission.enums.FollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentFollowUpRepository extends JpaRepository<DocumentFollowUp, Long> {

    Optional<DocumentFollowUp> findByFollowUpId(UUID followUpId);

    List<DocumentFollowUp> findByEnquiryIdInAndStatus(Collection<UUID> enquiryIds, FollowUpStatus status);

    List<DocumentFollowUp> findByTenantIdAndBranchIdAndStatusOrderByFollowUpDueDateAsc(String tenantId, String branchId,
                                                                                       FollowUpStatus status);

    List<DocumentFollowUp> findByTenantIdAndBranchIdOrderByFollowUpDueDateAsc(String tenantId, String branchId);
}
