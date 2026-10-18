package com.kvs.admission.repo;

import com.kvs.admission.entity.CallbackTask;
import com.kvs.admission.enums.CallbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallbackTaskRepository extends JpaRepository<CallbackTask, Long> {

    Optional<CallbackTask> findByCallbackId(UUID callbackId);

    List<CallbackTask> findByEnquiryIdAndStatus(UUID enquiryId, CallbackStatus status);

    List<CallbackTask> findByEnquiryIdOrderByScheduledAtDesc(UUID enquiryId);

    List<CallbackTask> findByTenantIdAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(String tenantId, CallbackStatus status,
                                                                                       Instant before);

    List<CallbackTask> findByTenantIdAndAssignedToAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            String tenantId, String assignedTo, CallbackStatus status, Instant before);
}
