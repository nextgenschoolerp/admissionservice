package com.kvs.admission.repo;

import com.kvs.admission.entity.AdmissionOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionOfferRepository extends JpaRepository<AdmissionOffer, Long> {

    Optional<AdmissionOffer> findByOfferId(UUID offerId);

    /** Locks the offer row so two cashiers cannot collect a deposit for the same offer concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AdmissionOffer o where o.offerId = :offerId")
    Optional<AdmissionOffer> findByOfferIdForUpdate(@Param("offerId") UUID offerId);

    List<AdmissionOffer> findByEnquiryIdOrderByCreatedAtDesc(UUID enquiryId);
}
