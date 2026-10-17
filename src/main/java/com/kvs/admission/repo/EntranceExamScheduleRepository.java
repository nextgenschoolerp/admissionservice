package com.kvs.admission.repo;

import com.kvs.admission.entity.EntranceExamSchedule;
import com.kvs.admission.enums.ExamScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntranceExamScheduleRepository extends JpaRepository<EntranceExamSchedule, Long> {

    Optional<EntranceExamSchedule> findByExamId(UUID examId);

    List<EntranceExamSchedule> findByTenantIdAndBranchIdAndStatusAndExamStartAtBetweenOrderByExamStartAtAsc(
            String tenantId, String branchId, ExamScheduleStatus status, Instant from, Instant to);

    List<EntranceExamSchedule> findByStatusAndExamStartAtBetweenOrderByExamStartAtAsc(
            ExamScheduleStatus status, Instant from, Instant to);
}
