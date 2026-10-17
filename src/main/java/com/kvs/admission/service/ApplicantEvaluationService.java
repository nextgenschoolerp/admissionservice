package com.kvs.admission.service;

import com.kvs.admission.dto.*;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.EntranceEvaluation;
import com.kvs.admission.entity.EvaluationRubricScore;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.EvaluationStatus;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.enums.RubricCriterion;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.EntranceEvaluationRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Service for scoring applicants against the standardized evaluation rubric.
 * Covers user story:
 * US-02003: Evaluate applicant interview scores using standardized rubric criteria
 * Actor: Registrar
 */
@Service
@Slf4j
public class ApplicantEvaluationService {

    /** Enquiry states in which an applicant may still be evaluated. */
    private static final Set<String> EVALUABLE_STATUSES = Set.of(
            EnquiryStatus.NEW_INQUIRY.name(),
            EnquiryStatus.CONTACTED.name(),
            EnquiryStatus.EVALUATION_SCHEDULED.name(),
            EnquiryStatus.EVALUATION_PASSED.name());

    @Autowired
    private EntranceEvaluationRepository evaluationRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Returns the standardized rubric criteria, optionally for one evaluation type.
     *
     * @param evaluationType evaluation type filter, or null for all types
     * @return rubric criteria with their maximum scores
     */
    public List<RubricCriterionResponse> getRubric(EvaluationType evaluationType) {
        List<RubricCriterion> criteria = evaluationType != null
                ? RubricCriterion.forType(evaluationType)
                : Arrays.asList(RubricCriterion.values());
        return criteria.stream()
                .map(c -> RubricCriterionResponse.builder()
                        .evaluationType(c.getEvaluationType().name())
                        .criterion(c.name())
                        .criterionName(c.getDisplayName())
                        .maxScore(c.getMaxScore())
                        .build())
                .toList();
    }

    /**
     * Records an evaluation scored against every criterion of the standardized rubric.
     * The total is the sum of awarded scores (each rubric totals 100). When all of an applicant's
     * evaluations are PASSED the enquiry moves to EVALUATION_PASSED.
     *
     * @param request the evaluation request
     * @return the recorded evaluation
     */
    @Transactional
    public EvaluationResponse recordEvaluation(RecordEvaluationRequest request) {
        log.info("Recording {} evaluation for enquiry: {}", request.getEvaluationType(), request.getEnquiryId());

        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(request.getEnquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", request.getEnquiryId()));

        if (!EVALUABLE_STATUSES.contains(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("Enquiry " + enquiry.getApplicationNumber() + " in status "
                    + enquiry.getEnquiryStatus() + " cannot be evaluated");
        }
        if (evaluationRepository.existsByEnquiryIdAndEvaluationType(enquiry.getEnquiryId(), request.getEvaluationType())) {
            throw new ConflictException("A " + request.getEvaluationType() + " evaluation already exists for enquiry "
                    + enquiry.getApplicationNumber());
        }

        Map<RubricCriterion, RubricScoreInput> scores = validateRubricScores(request);
        BigDecimal totalScore = scores.values().stream()
                .map(RubricScoreInput::getAwardedScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EvaluationStatus status = totalScore.compareTo(request.getPassingScore()) >= 0
                ? EvaluationStatus.PASSED
                : EvaluationStatus.FAILED;

        EntranceEvaluation evaluation = new EntranceEvaluation();
        evaluation.setTenantId(enquiry.getTenantId());
        evaluation.setEnquiryId(enquiry.getEnquiryId());
        evaluation.setEvaluationType(request.getEvaluationType());
        evaluation.setEvaluationDate(request.getEvaluationDate() != null ? request.getEvaluationDate() : Instant.now());
        evaluation.setExaminerId(request.getExaminerId() != null
                ? request.getExaminerId()
                : SecurityUtils.getCurrentUserEmail().orElse("registrar"));
        evaluation.setTotalScore(totalScore);
        evaluation.setPassingScore(request.getPassingScore());
        evaluation.setEvaluationStatus(status);
        evaluation.setFeedbackNotes(request.getFeedbackNotes());
        for (RubricCriterion criterion : RubricCriterion.forType(request.getEvaluationType())) {
            RubricScoreInput input = scores.get(criterion);
            EvaluationRubricScore score = new EvaluationRubricScore();
            score.setCriterion(criterion);
            score.setMaxScore(criterion.getMaxScore());
            score.setAwardedScore(input.getAwardedScore());
            score.setRemarks(input.getRemarks());
            evaluation.addRubricScore(score);
        }
        EntranceEvaluation saved = evaluationRepository.save(evaluation);

        updateEnquiryStatus(enquiry);

        log.info("Recorded {} evaluation {} for {}: score {} ({})", saved.getEvaluationType(),
                saved.getEvaluationId(), enquiry.getApplicationNumber(), totalScore, status);
        auditService.logSuccess("entrance_evaluation:" + enquiry.getTenantId(), "RECORD_APPLICANT_EVALUATION",
                "Recorded " + saved.getEvaluationType() + " evaluation for application " + enquiry.getApplicationNumber()
                        + " | Score: " + totalScore + "/100"
                        + " | Passing: " + request.getPassingScore()
                        + " | Result: " + status
                        + " | Examiner: " + saved.getExaminerId());

        return mapToResponse(saved, enquiry);
    }

    /**
     * Retrieves an evaluation with its rubric scores.
     *
     * @param evaluationId the evaluation UUID
     * @return the evaluation
     */
    @Transactional(readOnly = true)
    public EvaluationResponse getEvaluation(UUID evaluationId) {
        log.info("Fetching evaluation: {}", evaluationId);
        EntranceEvaluation evaluation = evaluationRepository.findByEvaluationId(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("EntranceEvaluation", "evaluationId", evaluationId));
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(evaluation.getEnquiryId()).orElse(null);
        return mapToResponse(evaluation, enquiry);
    }

    /**
     * Lists all evaluations recorded for an enquiry.
     *
     * @param enquiryId the enquiry UUID
     * @return evaluations ordered by evaluation date
     */
    @Transactional(readOnly = true)
    public List<EvaluationResponse> listEvaluations(UUID enquiryId) {
        log.info("Listing evaluations for enquiry: {}", enquiryId);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        return evaluationRepository.findByEnquiryIdOrderByEvaluationDateAsc(enquiryId).stream()
                .map(e -> mapToResponse(e, enquiry))
                .toList();
    }

    /**
     * Ensures exactly one score is supplied for every criterion of the rubric and none exceeds its maximum.
     */
    private Map<RubricCriterion, RubricScoreInput> validateRubricScores(RecordEvaluationRequest request) {
        EvaluationType type = request.getEvaluationType();
        Map<RubricCriterion, RubricScoreInput> scores = new EnumMap<>(RubricCriterion.class);
        for (RubricScoreInput input : request.getRubricScores()) {
            RubricCriterion criterion = input.getCriterion();
            if (criterion.getEvaluationType() != type) {
                throw new BadRequestException("Criterion " + criterion + " is not part of the " + type + " rubric");
            }
            if (scores.put(criterion, input) != null) {
                throw new BadRequestException("Criterion " + criterion + " is scored more than once");
            }
            if (input.getAwardedScore().compareTo(criterion.getMaxScore()) > 0) {
                throw new BadRequestException("Awarded score for " + criterion + " exceeds the maximum of "
                        + criterion.getMaxScore());
            }
        }
        List<RubricCriterion> missing = RubricCriterion.forType(type).stream()
                .filter(c -> !scores.containsKey(c))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing scores for rubric criteria: " + missing);
        }
        return scores;
    }

    private void updateEnquiryStatus(AdmissionEnquiry enquiry) {
        List<EntranceEvaluation> all = evaluationRepository.findByEnquiryIdOrderByEvaluationDateAsc(enquiry.getEnquiryId());
        boolean allPassed = all.stream().allMatch(e -> e.getEvaluationStatus() == EvaluationStatus.PASSED);
        if (allPassed) {
            enquiry.setEnquiryStatus(EnquiryStatus.EVALUATION_PASSED.name());
        } else if (EnquiryStatus.EVALUATION_PASSED.name().equals(enquiry.getEnquiryStatus())) {
            // A later failed evaluation withdraws the earlier pass
            enquiry.setEnquiryStatus(EnquiryStatus.EVALUATION_SCHEDULED.name());
        }
        admissionEnquiryRepository.save(enquiry);
    }

    private EvaluationResponse mapToResponse(EntranceEvaluation evaluation, AdmissionEnquiry enquiry) {
        List<RubricScoreResponse> rubric = evaluation.getRubricScores().stream()
                .map(s -> RubricScoreResponse.builder()
                        .criterion(s.getCriterion().name())
                        .criterionName(s.getCriterion().getDisplayName())
                        .maxScore(s.getMaxScore())
                        .awardedScore(s.getAwardedScore())
                        .remarks(s.getRemarks())
                        .build())
                .toList();
        BigDecimal maxTotal = evaluation.getRubricScores().stream()
                .map(EvaluationRubricScore::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return EvaluationResponse.builder()
                .evaluationId(evaluation.getEvaluationId())
                .tenantId(evaluation.getTenantId())
                .enquiryId(evaluation.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .evaluationType(evaluation.getEvaluationType().name())
                .evaluationDate(evaluation.getEvaluationDate())
                .examinerId(evaluation.getExaminerId())
                .totalScore(evaluation.getTotalScore())
                .maxTotalScore(maxTotal)
                .passingScore(evaluation.getPassingScore())
                .evaluationStatus(evaluation.getEvaluationStatus().name())
                .enquiryStatus(enquiry != null ? enquiry.getEnquiryStatus() : null)
                .feedbackNotes(evaluation.getFeedbackNotes())
                .rubricScores(rubric)
                .createdAt(evaluation.getCreatedAt())
                .build();
    }
}
