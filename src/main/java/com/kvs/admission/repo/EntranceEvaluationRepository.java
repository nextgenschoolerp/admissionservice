package com.kvs.admission.repo;

import com.kvs.admission.entity.EntranceEvaluation;
import com.kvs.admission.enums.EvaluationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntranceEvaluationRepository extends JpaRepository<EntranceEvaluation, Long> {

    @EntityGraph(attributePaths = "rubricScores")
    Optional<EntranceEvaluation> findByEvaluationId(UUID evaluationId);

    @EntityGraph(attributePaths = "rubricScores")
    List<EntranceEvaluation> findByEnquiryIdOrderByEvaluationDateAsc(UUID enquiryId);

    List<EntranceEvaluation> findByEnquiryIdIn(Collection<UUID> enquiryIds);

    boolean existsByEnquiryIdAndEvaluationType(UUID enquiryId, EvaluationType evaluationType);
}
