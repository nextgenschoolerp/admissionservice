package com.kvs.admission.repo;

import com.kvs.admission.entity.ArchivedLeadSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ArchivedLeadSnapshotRepository extends JpaRepository<ArchivedLeadSnapshot, Long> {

    List<ArchivedLeadSnapshot> findByTenantIdAndClosedAtGreaterThanEqualAndClosedAtLessThanOrderByClosedAtAsc(
            String tenantId, Instant from, Instant to);
}
