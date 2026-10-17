package com.kvs.admission.repo;

import com.kvs.admission.entity.AdmitCard;
import com.kvs.admission.enums.AdmitCardStatus;
import com.kvs.admission.enums.ExamScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdmitCardRepository extends JpaRepository<AdmitCard, Long> {

    List<AdmitCard> findByExamIdOrderByRollNumberAsc(UUID examId);

    List<AdmitCard> findByExamIdAndStatusOrderByRollNumberAsc(UUID examId, AdmitCardStatus status);

    /**
     * Returns the enquiries (from the given set) that already hold an issued admit card
     * for an exam that is still scheduled in the future.
     */
    @Query("select distinct a.enquiryId from AdmitCard a, EntranceExamSchedule e "
            + "where a.examId = e.examId and a.enquiryId in :enquiryIds "
            + "and a.status = :cardStatus and e.status = :examStatus and e.examStartAt > :now")
    List<UUID> findEnquiriesWithUpcomingAdmitCards(@Param("enquiryIds") Collection<UUID> enquiryIds,
                                                   @Param("cardStatus") AdmitCardStatus cardStatus,
                                                   @Param("examStatus") ExamScheduleStatus examStatus,
                                                   @Param("now") Instant now);
}
