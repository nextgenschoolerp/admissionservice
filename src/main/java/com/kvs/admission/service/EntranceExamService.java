package com.kvs.admission.service;

import com.kvs.admission.dto.AdmitCardResponse;
import com.kvs.admission.dto.EntranceExamResponse;
import com.kvs.admission.dto.ScheduleEntranceExamRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmitCard;
import com.kvs.admission.entity.EntranceExamSchedule;
import com.kvs.admission.enums.AdmitCardStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.ExamScheduleStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmitCardRepository;
import com.kvs.admission.repo.EntranceExamScheduleRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for entrance exam scheduling and admit card generation.
 * Covers user story:
 * US-02002: Schedule entrance examination dates and auto-generate candidate admit cards
 * Actor: Admission Officer
 */
@Service
@Slf4j
public class EntranceExamService {

    /** Enquiries in these states can no longer be scheduled for an entrance exam. */
    private static final Set<String> NON_SCHEDULABLE_STATUSES = Set.of(
            EnquiryStatus.OFFER_ISSUED.name(),
            EnquiryStatus.ENROLLED.name(),
            EnquiryStatus.REJECTED.name(),
            EnquiryStatus.WITHDRAWN.name());

    @Autowired
    private EntranceExamScheduleRepository examScheduleRepository;

    @Autowired
    private AdmitCardRepository admitCardRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Schedules an entrance exam and generates one admit card per candidate.
     * Every candidate must belong to the same tenant, branch, academic year and grade as the exam,
     * must be in a schedulable state, and must not already hold an admit card for another upcoming exam.
     *
     * @param request the exam schedule request
     * @return the scheduled exam with generated admit cards
     */
    @Transactional
    public EntranceExamResponse scheduleExam(ScheduleEntranceExamRequest request) {
        log.info("Scheduling entrance exam '{}' for tenant: {}, branch: {}, grade: {}, candidates: {}",
                request.getExamTitle(), request.getTenantId(), request.getBranchId(),
                request.getTargetGradeId(), request.getEnquiryIds().size());

        if (request.getPassingMarks().compareTo(request.getMaxMarks()) > 0) {
            throw new BadRequestException("Passing marks must not exceed maximum marks");
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(request.getEnquiryIds());
        if (uniqueIds.size() != request.getEnquiryIds().size()) {
            throw new BadRequestException("Duplicate enquiry IDs are not allowed in one exam schedule");
        }

        Map<UUID, AdmissionEnquiry> enquiries = admissionEnquiryRepository.findByEnquiryIdIn(uniqueIds).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        List<UUID> missing = uniqueIds.stream().filter(id -> !enquiries.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", missing);
        }

        for (AdmissionEnquiry enquiry : enquiries.values()) {
            validateCandidate(enquiry, request);
        }

        List<UUID> alreadyScheduled = admitCardRepository.findEnquiriesWithUpcomingAdmitCards(
                uniqueIds, AdmitCardStatus.ISSUED, ExamScheduleStatus.SCHEDULED, Instant.now());
        if (!alreadyScheduled.isEmpty()) {
            throw new ConflictException("Candidates already hold an admit card for an upcoming entrance exam: "
                    + alreadyScheduled);
        }

        EntranceExamSchedule exam = new EntranceExamSchedule();
        exam.setTenantId(request.getTenantId());
        exam.setBranchId(request.getBranchId());
        exam.setExamCode(ReferenceNumberGenerator.next("EXAM"));
        exam.setExamTitle(request.getExamTitle());
        exam.setAcademicYearId(request.getAcademicYearId());
        exam.setTargetGradeId(request.getTargetGradeId());
        exam.setExamStartAt(request.getExamStartAt());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setVenue(request.getVenue());
        exam.setMaxMarks(request.getMaxMarks());
        exam.setPassingMarks(request.getPassingMarks());
        exam.setReportingInstructions(request.getReportingInstructions());
        exam.setStatus(ExamScheduleStatus.SCHEDULED);
        EntranceExamSchedule savedExam = examScheduleRepository.save(exam);

        // Roll numbers follow the order in which candidates were submitted
        List<AdmitCard> admitCards = new ArrayList<>();
        int sequence = 1;
        for (UUID enquiryId : uniqueIds) {
            AdmissionEnquiry enquiry = enquiries.get(enquiryId);
            AdmitCard card = new AdmitCard();
            card.setTenantId(request.getTenantId());
            card.setExamId(savedExam.getExamId());
            card.setEnquiryId(enquiryId);
            card.setAdmitCardNumber(ReferenceNumberGenerator.next("ADMIT"));
            card.setRollNumber(String.format("%s-%03d", savedExam.getExamCode(), sequence));
            card.setSeatNumber(String.format("S-%03d", sequence));
            card.setCandidateName(enquiry.getCandidateFirstName() + " " + enquiry.getCandidateLastName());
            card.setStatus(AdmitCardStatus.ISSUED);
            admitCards.add(card);

            enquiry.setEnquiryStatus(EnquiryStatus.EVALUATION_SCHEDULED.name());
            sequence++;
        }
        List<AdmitCard> savedCards = admitCardRepository.saveAll(admitCards);
        admissionEnquiryRepository.saveAll(enquiries.values());

        log.info("Scheduled entrance exam {} with {} admit cards", savedExam.getExamCode(), savedCards.size());
        auditService.logSuccess("entrance_exam:" + request.getTenantId(), "SCHEDULE_ENTRANCE_EXAM",
                "Scheduled exam " + savedExam.getExamCode()
                        + " | Title: " + savedExam.getExamTitle()
                        + " | Start: " + savedExam.getExamStartAt()
                        + " | Venue: " + savedExam.getVenue()
                        + " | Admit cards generated: " + savedCards.size());

        return mapToResponse(savedExam, savedCards, enquiries);
    }

    /**
     * Retrieves an entrance exam with its admit cards.
     *
     * @param examId the exam UUID
     * @return the exam details
     */
    @Transactional(readOnly = true)
    public EntranceExamResponse getExam(UUID examId) {
        log.info("Fetching entrance exam: {}", examId);
        EntranceExamSchedule exam = findExam(examId);
        List<AdmitCard> cards = admitCardRepository.findByExamIdOrderByRollNumberAsc(examId);
        return mapToResponse(exam, cards, loadEnquiries(cards));
    }

    /**
     * Lists the admit cards generated for an exam.
     *
     * @param examId the exam UUID
     * @return admit cards ordered by roll number
     */
    @Transactional(readOnly = true)
    public List<AdmitCardResponse> listAdmitCards(UUID examId) {
        log.info("Listing admit cards for entrance exam: {}", examId);
        EntranceExamSchedule exam = findExam(examId);
        List<AdmitCard> cards = admitCardRepository.findByExamIdOrderByRollNumberAsc(examId);
        Map<UUID, AdmissionEnquiry> enquiries = loadEnquiries(cards);
        return cards.stream().map(card -> mapAdmitCard(card, exam, enquiries.get(card.getEnquiryId()))).toList();
    }

    private void validateCandidate(AdmissionEnquiry enquiry, ScheduleEntranceExamRequest request) {
        String applicationNumber = enquiry.getApplicationNumber();
        if (!enquiry.getTenantId().equals(request.getTenantId())
                || !enquiry.getBranchId().equals(request.getBranchId())) {
            throw new BadRequestException("Enquiry " + applicationNumber + " does not belong to this tenant/branch");
        }
        if (!enquiry.getAcademicYearId().equals(request.getAcademicYearId())
                || !enquiry.getTargetGradeId().equals(request.getTargetGradeId())) {
            throw new BadRequestException("Enquiry " + applicationNumber
                    + " targets a different grade or academic year than this exam");
        }
        if (NON_SCHEDULABLE_STATUSES.contains(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("Enquiry " + applicationNumber + " in status "
                    + enquiry.getEnquiryStatus() + " cannot be scheduled for an entrance exam");
        }
    }

    private EntranceExamSchedule findExam(UUID examId) {
        return examScheduleRepository.findByExamId(examId)
                .orElseThrow(() -> new ResourceNotFoundException("EntranceExamSchedule", "examId", examId));
    }

    private Map<UUID, AdmissionEnquiry> loadEnquiries(List<AdmitCard> cards) {
        Set<UUID> ids = cards.stream().map(AdmitCard::getEnquiryId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return admissionEnquiryRepository.findByEnquiryIdIn(ids).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
    }

    private EntranceExamResponse mapToResponse(EntranceExamSchedule exam, List<AdmitCard> cards,
                                               Map<UUID, AdmissionEnquiry> enquiries) {
        List<AdmitCardResponse> cardResponses = cards.stream()
                .map(card -> mapAdmitCard(card, exam, enquiries.get(card.getEnquiryId())))
                .toList();
        return EntranceExamResponse.builder()
                .id(exam.getId())
                .examId(exam.getExamId())
                .tenantId(exam.getTenantId())
                .branchId(exam.getBranchId())
                .examCode(exam.getExamCode())
                .examTitle(exam.getExamTitle())
                .academicYearId(exam.getAcademicYearId())
                .targetGradeId(exam.getTargetGradeId())
                .examStartAt(exam.getExamStartAt())
                .durationMinutes(exam.getDurationMinutes())
                .venue(exam.getVenue())
                .maxMarks(exam.getMaxMarks())
                .passingMarks(exam.getPassingMarks())
                .reportingInstructions(exam.getReportingInstructions())
                .status(exam.getStatus().name())
                .admitCardCount(cardResponses.size())
                .admitCards(cardResponses)
                .createdAt(exam.getCreatedAt())
                .build();
    }

    private AdmitCardResponse mapAdmitCard(AdmitCard card, EntranceExamSchedule exam, AdmissionEnquiry enquiry) {
        return AdmitCardResponse.builder()
                .admitCardId(card.getAdmitCardId())
                .admitCardNumber(card.getAdmitCardNumber())
                .examId(exam.getExamId())
                .examCode(exam.getExamCode())
                .examTitle(exam.getExamTitle())
                .enquiryId(card.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .candidateName(card.getCandidateName())
                .rollNumber(card.getRollNumber())
                .seatNumber(card.getSeatNumber())
                .examStartAt(exam.getExamStartAt())
                .durationMinutes(exam.getDurationMinutes())
                .venue(exam.getVenue())
                .reportingInstructions(exam.getReportingInstructions())
                .status(card.getStatus().name())
                .build();
    }
}
