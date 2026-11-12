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

    /** Every exam of a campus, newest exam date first (admissions workspace → Entrance exams). */
    List<EntranceExamSchedule> findByTenantIdAndBranchIdOrderByExamStartAtDesc(String tenantId, String branchId);

    List<EntranceExamSchedule> findByTenantIdAndBranchIdAndStatusAndExamStartAtBetweenOrderByExamStartAtAsc(
            String tenantId, String branchId, ExamScheduleStatus status, Instant from, Instant to);

    List<EntranceExamSchedule> findByStatusAndExamStartAtBetweenOrderByExamStartAtAsc(
            ExamScheduleStatus status, Instant from, Instant to);
}
