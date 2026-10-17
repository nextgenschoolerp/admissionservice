package com.kvs.admission.repo;

import com.kvs.admission.entity.ApplicantNotification;
import com.kvs.admission.enums.NotificationChannel;
import com.kvs.admission.enums.NotificationStatus;
import com.kvs.admission.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicantNotificationRepository extends JpaRepository<ApplicantNotification, Long> {

    /** Admit cards (from the given set) that already received a successful notification of this type. */
    @Query("select distinct n.admitCardId from ApplicantNotification n where n.admitCardId in :admitCardIds "
            + "and n.notificationType = :type and n.channel = :channel and n.status = :status")
    List<UUID> findNotifiedAdmitCards(@Param("admitCardIds") Collection<UUID> admitCardIds,
                                      @Param("type") NotificationType type,
                                      @Param("channel") NotificationChannel channel,
                                      @Param("status") NotificationStatus status);

    List<ApplicantNotification> findByEnquiryIdOrderByDispatchedAtDesc(UUID enquiryId);
}
