package com.kvs.admission.repo;

import com.kvs.admission.entity.CounselingCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CounselingCallRepository extends JpaRepository<CounselingCall, Long> {

    List<CounselingCall> findByEnquiryIdOrderByCalledAtDesc(UUID enquiryId);
}
