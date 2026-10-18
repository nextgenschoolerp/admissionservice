package com.kvs.admission.service;

import com.kvs.admission.dto.*;
import com.kvs.admission.entity.*;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.enums.OfferStatus;
import com.kvs.admission.enums.WaitlistStatus;
import com.kvs.admission.repo.*;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service managing grade waitlists and escalating free seats to waiting candidates.
 * Covers user stories:
 * US-02014, US-02034, US-02054, US-02074, US-02094: Manage applicant waitlist queues and automate seat escalation rules
 * Actor: Admission Officer (Finance Cashier in US-02014/54/94 is not granted: seat allocation is an admissions decision)
 */
@Service
@Slf4j
public class WaitlistService {

    private static final Set<WaitlistStatus> ACTIVE_ENTRY = EnumSet.of(WaitlistStatus.WAITING, WaitlistStatus.OFFERED);
    private static final Set<OfferStatus> LIVE_OFFER = EnumSet.of(OfferStatus.DISPATCHED, OfferStatus.ACCEPTED);
    private static final Comparator<WaitlistEntry> QUEUE_ORDER = Comparator
            .comparing(WaitlistEntry::getPriorityScore, Comparator.reverseOrder())
            .thenComparing(WaitlistEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    @Autowired
    private WaitlistRuleRepository ruleRepository;

    @Autowired
    private WaitlistEntryRepository entryRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private StudentMasterRecordRepository studentRepository;

    @Autowired
    private MeritListRepository meritListRepository;

    @Autowired
    private EntranceEvaluationRepository evaluationRepository;

    @Autowired
    private AdmissionOfferService offerService;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Creates or updates the seat rule of a grade.
     */
    @Transactional
    public WaitlistResponse saveRule(WaitlistRuleRequest request) {
        WaitlistRule rule = ruleRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeId(request.getTenantId(),
                request.getBranchId(), request.getAcademicYearId(), request.getGradeId()).orElseGet(WaitlistRule::new);
        rule.setTenantId(request.getTenantId());
        rule.setBranchId(request.getBranchId());
        rule.setAcademicYearId(request.getAcademicYearId());
        rule.setGradeId(request.getGradeId());
        rule.setTotalSeats(request.getTotalSeats());
        rule.setOfferValidityDays(request.getOfferValidityDays());
        rule.setSeatDepositAmount(request.getSeatDepositAmount());
        rule.setAutoEscalationEnabled(request.isAutoEscalationEnabled());
        ruleRepository.save(rule);
        auditService.logSuccess("waitlist:" + request.getTenantId(), "SAVE_WAITLIST_RULE",
                "Grade " + request.getGradeId() + " (" + request.getAcademicYearId() + ", " + request.getBranchId() + ") | Seats: "
                        + request.getTotalSeats() + " | Offer validity: " + request.getOfferValidityDays() + " days | Auto: "
                        + request.isAutoEscalationEnabled());
        return getWaitlist(request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId());
    }

    /**
     * Adds an evaluated candidate to the waitlist of the grade they applied for.
     * Priority is the candidate's weighted score in the latest merit list of the grade, falling back
     * to the entrance test score.
     */
    @Transactional
    public WaitlistEntryResponse addToWaitlist(AddToWaitlistRequest request) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(request.getEnquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", request.getEnquiryId()));
        findRule(enquiry.getTenantId(), enquiry.getBranchId(), enquiry.getAcademicYearId(), enquiry.getTargetGradeId());
        if (!EnquiryStatus.EVALUATION_PASSED.name().equals(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("Only candidates who passed evaluation can be waitlisted; "
                    + enquiry.getApplicationNumber() + " is " + enquiry.getEnquiryStatus());
        }
        if (hasLiveOffer(enquiry.getEnquiryId())) {
            throw new ConflictException(enquiry.getApplicationNumber() + " already holds a live offer");
        }
        if (entryRepository.existsByEnquiryIdAndStatusIn(enquiry.getEnquiryId(), ACTIVE_ENTRY)) {
            throw new ConflictException(enquiry.getApplicationNumber() + " is already on the waitlist");
        }

        WaitlistEntry entry = new WaitlistEntry();
        entry.setTenantId(enquiry.getTenantId());
        entry.setBranchId(enquiry.getBranchId());
        entry.setAcademicYearId(enquiry.getAcademicYearId());
        entry.setGradeId(enquiry.getTargetGradeId());
        entry.setEnquiryId(enquiry.getEnquiryId());
        entry.setPriorityScore(priorityFor(enquiry));
        entry.setStatus(WaitlistStatus.WAITING);
        WaitlistEntry saved = entryRepository.save(entry);
        auditService.logSuccess("waitlist:" + enquiry.getTenantId(), "ADD_TO_WAITLIST",
                "Waitlisted " + enquiry.getApplicationNumber() + " for grade " + enquiry.getTargetGradeId()
                        + " with priority " + saved.getPriorityScore());
        return mapEntry(saved, enquiry, null);
    }

    /**
     * Removes a waiting candidate from the queue.
     */
    @Transactional
    public WaitlistEntryResponse removeFromWaitlist(UUID entryId, String reason) {
        WaitlistEntry entry = entryRepository.findByEntryId(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("WaitlistEntry", "entryId", entryId));
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            throw new ConflictException("Waitlist entry " + entryId + " is " + entry.getStatus() + " and cannot be removed");
        }
        entry.setStatus(WaitlistStatus.REMOVED);
        entry.setRemovalReason(reason != null && !reason.isBlank() ? reason : "Removed by admissions office");
        WaitlistEntry saved = entryRepository.save(entry);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(saved.getEnquiryId()).orElse(null);
        auditService.logSuccess("waitlist:" + saved.getTenantId(), "REMOVE_FROM_WAITLIST",
                "Removed " + (enquiry != null ? enquiry.getApplicationNumber() : saved.getEnquiryId()) + " | Reason: " + saved.getRemovalReason());
        return mapEntry(saved, enquiry, null);
    }

    /**
     * Returns a grade waitlist with seat occupancy and queue positions.
     */
    @Transactional(readOnly = true)
    public WaitlistResponse getWaitlist(String tenantId, String branchId, String academicYearId, String gradeId) {
        WaitlistRule rule = findRule(tenantId, branchId, academicYearId, gradeId);
        List<WaitlistEntry> entries = entryRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatusIn(
                tenantId, branchId, academicYearId, gradeId, ACTIVE_ENTRY);
        int occupied = occupiedSeats(rule);
        return buildResponse(rule, entries, occupied);
    }

    /**
     * Escalates free seats of one grade: lapses expired offers, then issues offers to the highest-priority
     * waiting candidates until the seats are filled.
     */
    @Transactional
    public WaitlistEscalationResponse escalate(WaitlistScopeRequest request) {
        WaitlistRule rule = findRule(request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId());
        int expired = expireLapsedOffers(rule);
        int free = freeSeats(rule.getTotalSeats(), occupiedSeats(rule));

        List<WaitlistEntry> waiting = entryRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatusIn(
                        rule.getTenantId(), rule.getBranchId(), rule.getAcademicYearId(), rule.getGradeId(), EnumSet.of(WaitlistStatus.WAITING))
                .stream().sorted(QUEUE_ORDER).toList();
        Map<UUID, AdmissionEnquiry> enquiries = loadEnquiries(waiting);

        List<AdmissionOfferResponse> issued = new ArrayList<>();
        int removed = 0;
        int remainingSeats = free;
        for (WaitlistEntry entry : waiting) {
            if (remainingSeats == 0) {
                break;
            }
            AdmissionEnquiry enquiry = enquiries.get(entry.getEnquiryId());
            // Check eligibility up front: a failed issueOffer would roll back the whole escalation
            if (enquiry == null || !EnquiryStatus.EVALUATION_PASSED.name().equals(enquiry.getEnquiryStatus())
                    || hasLiveOffer(enquiry.getEnquiryId())) {
                entry.setStatus(WaitlistStatus.REMOVED);
                entry.setRemovalReason("No longer eligible" + (enquiry != null ? " (status " + enquiry.getEnquiryStatus() + ")" : ""));
                entryRepository.save(entry);
                removed++;
                continue;
            }
            AdmissionOfferResponse offer = offerService.issueOffer(new IssueOfferRequest(enquiry.getEnquiryId(), rule.getGradeId(),
                    LocalDate.now().plusDays(rule.getOfferValidityDays()), rule.getSeatDepositAmount()));
            entry.setStatus(WaitlistStatus.OFFERED);
            entry.setOfferId(offer.getOfferId());
            entry.setOfferedAt(Instant.now());
            entryRepository.save(entry);
            issued.add(offer);
            remainingSeats--;
        }
        int stillWaiting = (int) waiting.stream().filter(e -> e.getStatus() == WaitlistStatus.WAITING).count();

        log.info("Waitlist escalation for {}: expired={}, free={}, issued={}, removed={}", rule.getGradeId(), expired, free,
                issued.size(), removed);
        auditService.logSuccess("waitlist:" + rule.getTenantId(), "ESCALATE_WAITLIST",
                "Grade " + rule.getGradeId() + " (" + rule.getAcademicYearId() + ", " + rule.getBranchId() + ") | Lapsed offers: " + expired
                        + " | Free seats: " + free + " | Offers issued: " + issued.size() + " | Removed: " + removed
                        + " | Still waiting: " + stillWaiting);
        return WaitlistEscalationResponse.builder()
                .gradeId(rule.getGradeId())
                .expiredOffers(expired)
                .freeSeatsBefore(free)
                .offersIssued(issued.size())
                .removedIneligible(removed)
                .stillWaiting(stillWaiting)
                .issuedOffers(issued)
                .build();
    }

    /**
     * Scheduled escalation of every grade whose rule has auto escalation enabled.
     */
    @Transactional
    public int escalateAllAutomatic() {
        int issued = 0;
        for (WaitlistRule rule : ruleRepository.findByAutoEscalationEnabledTrue()) {
            issued += escalate(new WaitlistScopeRequest(rule.getTenantId(), rule.getBranchId(), rule.getAcademicYearId(),
                    rule.getGradeId())).getOffersIssued();
        }
        return issued;
    }

    static int freeSeats(int totalSeats, int occupiedSeats) {
        return Math.max(0, totalSeats - occupiedSeats);
    }

    /** Enrolled students of the grade plus live offers whose candidate has not enrolled yet. */
    private int occupiedSeats(WaitlistRule rule) {
        long enrolled = studentRepository.countByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatus(
                rule.getTenantId(), rule.getBranchId(), rule.getAcademicYearId(), rule.getGradeId(), "ACTIVE");
        List<AdmissionOffer> live = offerRepository.findByTenantIdAndAcademicYearIdAndOfferedGradeIdAndOfferStatusIn(
                        rule.getTenantId(), rule.getAcademicYearId(), rule.getGradeId(), LIVE_OFFER).stream()
                .filter(o -> o.getOfferStatus() == OfferStatus.ACCEPTED || !AdmissionOfferService.isPastValidity(o))
                .toList();
        Map<UUID, AdmissionEnquiry> enquiries = live.isEmpty() ? Map.of()
                : admissionEnquiryRepository.findByEnquiryIdIn(live.stream().map(AdmissionOffer::getEnquiryId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        long pending = live.stream()
                .map(o -> enquiries.get(o.getEnquiryId()))
                .filter(e -> e != null && rule.getBranchId().equals(e.getBranchId())
                        && !EnquiryStatus.ENROLLED.name().equals(e.getEnquiryStatus()))
                .count();
        return (int) (enrolled + pending);
    }

    /** Marks dispatched offers past their validity date EXPIRED and returns the candidates to EVALUATION_PASSED. */
    private int expireLapsedOffers(WaitlistRule rule) {
        List<AdmissionOffer> lapsed = offerRepository.findByTenantIdAndAcademicYearIdAndOfferedGradeIdAndOfferStatusIn(
                        rule.getTenantId(), rule.getAcademicYearId(), rule.getGradeId(), EnumSet.of(OfferStatus.DISPATCHED)).stream()
                .filter(AdmissionOfferService::isPastValidity)
                .toList();
        if (lapsed.isEmpty()) {
            return 0;
        }
        Map<UUID, AdmissionEnquiry> enquiries = admissionEnquiryRepository.findByEnquiryIdIn(
                        lapsed.stream().map(AdmissionOffer::getEnquiryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        Map<UUID, WaitlistEntry> entriesByOffer = entryRepository.findByOfferIdIn(
                        lapsed.stream().map(AdmissionOffer::getOfferId).toList()).stream()
                .collect(Collectors.toMap(WaitlistEntry::getOfferId, Function.identity()));
        int count = 0;
        for (AdmissionOffer offer : lapsed) {
            AdmissionEnquiry enquiry = enquiries.get(offer.getEnquiryId());
            if (enquiry == null || !rule.getBranchId().equals(enquiry.getBranchId())) {
                continue;
            }
            offer.setOfferStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            if (EnquiryStatus.OFFER_ISSUED.name().equals(enquiry.getEnquiryStatus())) {
                enquiry.setEnquiryStatus(EnquiryStatus.EVALUATION_PASSED.name());
                admissionEnquiryRepository.save(enquiry);
            }
            WaitlistEntry entry = entriesByOffer.get(offer.getOfferId());
            if (entry != null) {
                entry.setStatus(WaitlistStatus.REMOVED);
                entry.setRemovalReason("Offer " + offer.getOfferCode() + " lapsed on " + offer.getValidUntilDate());
                entryRepository.save(entry);
            }
            count++;
        }
        return count;
    }

    private boolean hasLiveOffer(UUID enquiryId) {
        return offerRepository.findByEnquiryIdOrderByCreatedAtDesc(enquiryId).stream()
                .anyMatch(o -> o.getOfferStatus() == OfferStatus.ACCEPTED
                        || (o.getOfferStatus() == OfferStatus.DISPATCHED && !AdmissionOfferService.isPastValidity(o)));
    }

    private BigDecimal priorityFor(AdmissionEnquiry enquiry) {
        Optional<BigDecimal> merit = meritListRepository.findFirstByTenantIdAndBranchIdAndAcademicYearIdAndTargetGradeIdOrderByCreatedAtDesc(
                        enquiry.getTenantId(), enquiry.getBranchId(), enquiry.getAcademicYearId(), enquiry.getTargetGradeId())
                .flatMap(list -> list.getEntries().stream()
                        .filter(e -> e.getEnquiryId().equals(enquiry.getEnquiryId()))
                        .map(MeritListEntry::getWeightedScore)
                        .findFirst());
        return merit.orElseGet(() -> evaluationRepository.findByEnquiryIdOrderByEvaluationDateAsc(enquiry.getEnquiryId()).stream()
                .filter(e -> e.getEvaluationType() == EvaluationType.ENTRANCE_TEST)
                .map(EntranceEvaluation::getTotalScore)
                .findFirst()
                .orElse(BigDecimal.ZERO));
    }

    private WaitlistRule findRule(String tenantId, String branchId, String academicYearId, String gradeId) {
        return ruleRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeId(tenantId, branchId, academicYearId, gradeId)
                .orElseThrow(() -> new ResourceNotFoundException("WaitlistRule", "grade", gradeId + " / " + academicYearId + " / " + branchId));
    }

    private Map<UUID, AdmissionEnquiry> loadEnquiries(List<WaitlistEntry> entries) {
        Set<UUID> ids = entries.stream().map(WaitlistEntry::getEnquiryId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : admissionEnquiryRepository.findByEnquiryIdIn(ids).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
    }

    private WaitlistResponse buildResponse(WaitlistRule rule, List<WaitlistEntry> entries, int occupied) {
        Map<UUID, AdmissionEnquiry> enquiries = loadEnquiries(entries);
        List<WaitlistEntry> ordered = entries.stream().sorted(Comparator
                .comparing((WaitlistEntry e) -> e.getStatus() == WaitlistStatus.WAITING ? 0 : 1)
                .thenComparing(QUEUE_ORDER)).toList();
        List<WaitlistEntryResponse> responses = new ArrayList<>();
        int position = 1;
        for (WaitlistEntry e : ordered) {
            Integer pos = e.getStatus() == WaitlistStatus.WAITING ? position++ : null;
            responses.add(mapEntry(e, enquiries.get(e.getEnquiryId()), pos));
        }
        return WaitlistResponse.builder()
                .tenantId(rule.getTenantId())
                .branchId(rule.getBranchId())
                .academicYearId(rule.getAcademicYearId())
                .gradeId(rule.getGradeId())
                .totalSeats(rule.getTotalSeats())
                .offerValidityDays(rule.getOfferValidityDays())
                .seatDepositAmount(rule.getSeatDepositAmount())
                .autoEscalationEnabled(rule.isAutoEscalationEnabled())
                .occupiedSeats(occupied)
                .freeSeats(freeSeats(rule.getTotalSeats(), occupied))
                .entries(responses)
                .build();
    }

    private WaitlistEntryResponse mapEntry(WaitlistEntry e, AdmissionEnquiry enquiry, Integer position) {
        return WaitlistEntryResponse.builder()
                .entryId(e.getEntryId())
                .position(position)
                .enquiryId(e.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .candidateName(enquiry != null ? enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName() : null)
                .priorityScore(e.getPriorityScore())
                .status(e.getStatus().name())
                .offerId(e.getOfferId())
                .offeredAt(e.getOfferedAt())
                .removalReason(e.getRemovalReason())
                .addedAt(e.getCreatedAt())
                .build();
    }
}
