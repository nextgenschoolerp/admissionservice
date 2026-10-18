package com.kvs.admission.repo;

import com.kvs.admission.entity.TcVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TcVerificationRepository extends JpaRepository<TcVerification, Long> {

    List<TcVerification> findByEnquiryIdOrderByCheckedAtDesc(UUID enquiryId);
}
