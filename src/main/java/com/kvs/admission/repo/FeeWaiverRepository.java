package com.kvs.admission.repo;

import com.kvs.admission.entity.FeeWaiver;
import com.kvs.admission.enums.WaiverStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeWaiverRepository extends JpaRepository<FeeWaiver, Long> {

    Optional<FeeWaiver> findByWaiverId(UUID waiverId);

    Optional<FeeWaiver> findFirstByOfferIdAndStatus(UUID offerId, WaiverStatus status);

    boolean existsByOfferIdAndStatusIn(UUID offerId, Collection<WaiverStatus> statuses);

    List<FeeWaiver> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<FeeWaiver> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, WaiverStatus status);
}
