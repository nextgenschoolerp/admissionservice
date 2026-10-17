package com.kvs.admission.repo;

import com.kvs.admission.entity.AdmissionFeeDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdmissionFeeDepositRepository extends JpaRepository<AdmissionFeeDeposit, Long> {

    Optional<AdmissionFeeDeposit> findByReceiptNumber(String receiptNumber);

    boolean existsByOfferId(UUID offerId);

    boolean existsByTenantIdAndTransactionReference(String tenantId, String transactionReference);
}
