package com.kvs.admission.repo;

import com.kvs.admission.entity.StudentMasterRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentMasterRecordRepository extends JpaRepository<StudentMasterRecord, Long> {

    Optional<StudentMasterRecord> findByStudentId(UUID studentId);

    boolean existsByEnquiryId(UUID enquiryId);
}
