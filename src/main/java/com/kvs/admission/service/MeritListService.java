package com.kvs.admission.service;

import com.kvs.admission.dto.GenerateMeritListRequest;
import com.kvs.admission.dto.MeritListEntryResponse;
import com.kvs.admission.dto.MeritListResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.EntranceEvaluation;
import com.kvs.admission.entity.MeritList;
import com.kvs.admission.entity.MeritListEntry;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.EvaluationStatus;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.EntranceEvaluationRepository;
import com.kvs.admission.repo.MeritListRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service generating merit lists from weighted evaluation scores.
 * Covers user story:
 * US-02010: Rank candidates automatically based on weighted entrance test scores
 * Actor: Admission Officer
 */
@Service
@Slf4j
public class MeritListService {

    private static final Set<String> EXCLUDED_STATUSES = Set.of(
            EnquiryStatus.REJECTED.name(), EnquiryStatus.WITHDRAWN.name());

    @Autowired
    private MeritListRepository meritListRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private EntranceEvaluationRepository evaluationRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Ranks every evaluated candidate of a grade/academic year by weighted score and stores the merit list.
     * A candidate is ranked only when it has an evaluation for each component whose weight is above zero;
     * rejected and withdrawn leads are excluded.
     *
     * @param request the ranking scope and weights
     * @return the generated merit list
     */
    @Transactional
    public MeritListResponse generateMeritList(GenerateMeritListRequest request) {
        BigDecimal entranceWeight = request.getEntranceTestWeight();
        BigDecimal interviewWeight = request.getInterviewWeight();
        if (entranceWeight.add(interviewWeight).compareTo(BigDecimal.ONE) != 0) {
            throw new BadRequestException("Entrance test weight and interview weight must add up to 1");
        }
        if (entranceWeight.signum() == 0) {
            throw new BadRequestException("Entrance test weight must be greater than zero");
        }
        log.info("Generating merit list for tenant: {}, branch: {}, grade: {}, year: {} (weights {} / {})",
                request.getTenantId(), request.getBranchId(), request.getTargetGradeId(),
                request.getAcademicYearId(), entranceWeight, interviewWeight);

        List<AdmissionEnquiry> enquiries = admissionEnquiryRepository
                .findByTenantIdAndBranchIdAndAcademicYearIdAndTargetGradeId(request.getTenantId(),
                        request.getBranchId(), request.getAcademicYearId(), request.getTargetGradeId());
        Map<UUID, AdmissionEnquiry> enquiryById = enquiries.stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        Map<UUID, Map<EvaluationType, EntranceEvaluation>> evaluations = enquiryById.isEmpty()
                ? Map.of()
                : evaluationRepository.findByEnquiryIdIn(enquiryById.keySet()).stream()
                .collect(Collectors.groupingBy(EntranceEvaluation::getEnquiryId,
                        Collectors.toMap(EntranceEvaluation::getEvaluationType, Function.identity())));

        List<MeritRankingCalculator.Candidate> candidates = new ArrayList<>();
        int excluded = 0;
        for (AdmissionEnquiry enquiry : enquiries) {
            Map<EvaluationType, EntranceEvaluation> evals = evaluations.getOrDefault(enquiry.getEnquiryId(), Map.of());
            EntranceEvaluation entrance = evals.get(EvaluationType.ENTRANCE_TEST);
            EntranceEvaluation interview = evals.get(EvaluationType.INTERVIEW);
            boolean missingComponent = entrance == null || (interviewWeight.signum() > 0 && interview == null);
            if (evals.isEmpty()) {
                continue; // not yet evaluated: not part of this ranking
            }
            if (missingComponent || EXCLUDED_STATUSES.contains(enquiry.getEnquiryStatus())) {
                excluded++;
                continue;
            }
            candidates.add(new MeritRankingCalculator.Candidate(enquiry.getEnquiryId(),
                    entrance.getTotalScore(),
                    interview != null ? interview.getTotalScore() : null,
                    enquiry.getCreatedAt()));
        }
        if (candidates.isEmpty()) {
            throw new BadRequestException("No candidates with complete evaluations found for grade "
                    + request.getTargetGradeId() + " in academic year " + request.getAcademicYearId());
        }

        List<MeritRankingCalculator.RankedCandidate> ranked =
                MeritRankingCalculator.rank(candidates, entranceWeight, interviewWeight);

        MeritList meritList = new MeritList();
        meritList.setMeritListCode(ReferenceNumberGenerator.next("MERIT"));
        meritList.setTenantId(request.getTenantId());
        meritList.setBranchId(request.getBranchId());
        meritList.setAcademicYearId(request.getAcademicYearId());
        meritList.setTargetGradeId(request.getTargetGradeId());
        meritList.setEntranceTestWeight(entranceWeight);
        meritList.setInterviewWeight(interviewWeight);
        meritList.setRankedCandidates(ranked.size());
        meritList.setExcludedCandidates(excluded);
        meritList.setGeneratedBy(SecurityUtils.getCurrentUserEmail().orElse("admission_officer"));
        for (MeritRankingCalculator.RankedCandidate r : ranked) {
            AdmissionEnquiry enquiry = enquiryById.get(r.candidate().enquiryId());
            Map<EvaluationType, EntranceEvaluation> evals = evaluations.get(enquiry.getEnquiryId());
            MeritListEntry entry = new MeritListEntry();
            entry.setEnquiryId(enquiry.getEnquiryId());
            entry.setApplicationNumber(enquiry.getApplicationNumber());
            entry.setCandidateName(enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName());
            entry.setEntranceTestScore(r.candidate().entranceTestScore());
            entry.setInterviewScore(r.candidate().interviewScore());
            entry.setWeightedScore(r.weightedScore());
            entry.setMeritRank(r.rank());
            entry.setQualified(evals.values().stream().allMatch(e -> e.getEvaluationStatus() == EvaluationStatus.PASSED));
            meritList.addEntry(entry);
        }
        MeritList saved = meritListRepository.save(meritList);

        log.info("Generated merit list {} with {} ranked and {} excluded candidates",
                saved.getMeritListCode(), ranked.size(), excluded);
        auditService.logSuccess("merit_list:" + request.getTenantId(), "GENERATE_MERIT_LIST",
                "Generated " + saved.getMeritListCode()
                        + " | Grade: " + request.getTargetGradeId()
                        + " | Year: " + request.getAcademicYearId()
                        + " | Weights: entrance " + entranceWeight + ", interview " + interviewWeight
                        + " | Ranked: " + ranked.size() + " | Excluded: " + excluded);

        return mapToResponse(saved);
    }

    /**
     * Retrieves a merit list with its ranked entries.
     *
     * @param meritListId the merit list UUID
     * @return the merit list
     */
    @Transactional(readOnly = true)
    public MeritListResponse getMeritList(UUID meritListId) {
        log.info("Fetching merit list: {}", meritListId);
        MeritList meritList = meritListRepository.findByMeritListId(meritListId)
                .orElseThrow(() -> new ResourceNotFoundException("MeritList", "meritListId", meritListId));
        return mapToResponse(meritList);
    }

    private MeritListResponse mapToResponse(MeritList list) {
        List<MeritListEntryResponse> entries = list.getEntries().stream()
                .sorted(Comparator.comparing(MeritListEntry::getMeritRank))
                .map(e -> MeritListEntryResponse.builder()
                        .meritRank(e.getMeritRank())
                        .enquiryId(e.getEnquiryId())
                        .applicationNumber(e.getApplicationNumber())
                        .candidateName(e.getCandidateName())
                        .entranceTestScore(e.getEntranceTestScore())
                        .interviewScore(e.getInterviewScore())
                        .weightedScore(e.getWeightedScore())
                        .qualified(e.isQualified())
                        .build())
                .toList();
        return MeritListResponse.builder()
                .meritListId(list.getMeritListId())
                .meritListCode(list.getMeritListCode())
                .tenantId(list.getTenantId())
                .branchId(list.getBranchId())
                .academicYearId(list.getAcademicYearId())
                .targetGradeId(list.getTargetGradeId())
                .entranceTestWeight(list.getEntranceTestWeight())
                .interviewWeight(list.getInterviewWeight())
                .rankedCandidates(list.getRankedCandidates())
                .excludedCandidates(list.getExcludedCandidates())
                .generatedBy(list.getGeneratedBy())
                .entries(entries)
                .createdAt(list.getCreatedAt())
                .build();
    }
}
