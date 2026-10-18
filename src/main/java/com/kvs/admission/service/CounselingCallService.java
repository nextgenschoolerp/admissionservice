package com.kvs.admission.service;

import com.kvs.admission.dto.CallbackResponse;
import com.kvs.admission.dto.CancelCallbackRequest;
import com.kvs.admission.dto.CounselingCallResponse;
import com.kvs.admission.dto.LogCallRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.CallbackTask;
import com.kvs.admission.entity.CounselingCall;
import com.kvs.admission.enums.CallOutcome;
import com.kvs.admission.enums.CallbackStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.CallbackTaskRepository;
import com.kvs.admission.repo.CounselingCallRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service logging counseling calls and scheduling callbacks automatically.
 * Covers user stories:
 * US-02015, US-02035, US-02055, US-02075, US-02095: Log telephone counseling call notes and schedule automated callbacks
 * Calls are logged by the Front-Office Counselor / Admission Officer; the Marketing Manager (US-02015/55/95) has
 * read access to call history for campaign follow-up. The Registrar (US-02035/75) is not granted.
 */
@Service
@Slf4j
public class CounselingCallService {

    static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
    static final LocalTime CALLING_STARTS = LocalTime.of(9, 0);
    static final LocalTime CALLING_ENDS = LocalTime.of(19, 0);
    static final LocalTime NEXT_DAY_SLOT = LocalTime.of(10, 0);
    static final int MAX_UNANSWERED_ATTEMPTS = 3;

    private static final Set<CallOutcome> UNANSWERED = EnumSet.of(CallOutcome.NO_ANSWER, CallOutcome.BUSY);

    @Autowired
    private CounselingCallRepository callRepository;

    @Autowired
    private CallbackTaskRepository callbackRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Logs a call. Any callbacks still scheduled for the enquiry are completed by this call, and a new
     * callback is scheduled from the outcome (see {@link #computeCallback}). A connected call moves a new
     * enquiry to CONTACTED; "not interested" before evaluation withdraws it.
     */
    @Transactional
    public CounselingCallResponse logCall(UUID enquiryId, LogCallRequest request) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        if (enquiry.getArchivedAt() != null) {
            throw new BadRequestException("Enquiry " + enquiry.getApplicationNumber() + " is archived");
        }
        if (request.getOutcome() == CallOutcome.CALLBACK_REQUESTED && request.getCallbackAt() == null) {
            throw new BadRequestException("callbackAt is required when the family requests a callback");
        }
        String counselor = SecurityUtils.getCurrentUserEmail().orElse("front_office_counselor");
        Instant calledAt = request.getCalledAt() != null ? request.getCalledAt() : Instant.now();

        CounselingCall call = new CounselingCall();
        call.setTenantId(enquiry.getTenantId());
        call.setEnquiryId(enquiryId);
        call.setDirection(request.getDirection());
        call.setOutcome(request.getOutcome());
        call.setDurationSeconds(request.getDurationSeconds());
        call.setNotes(request.getNotes());
        call.setCalledAt(calledAt);
        call.setCounselorId(counselor);
        CounselingCall saved = callRepository.save(call);

        for (CallbackTask pending : callbackRepository.findByEnquiryIdAndStatus(enquiryId, CallbackStatus.SCHEDULED)) {
            pending.setStatus(CallbackStatus.COMPLETED);
            pending.setClosedAt(calledAt);
            pending.setClosingNote("Completed by call " + saved.getCallId() + " (" + request.getOutcome() + ")");
            callbackRepository.save(pending);
        }

        int unansweredStreak = unansweredStreak(callRepository.findByEnquiryIdOrderByCalledAtDesc(enquiryId));
        Optional<Instant> callbackAt = computeCallback(request.getOutcome(), calledAt, request.getCallbackAt(), unansweredStreak);
        CallbackTask callback = null;
        String callbackNote = null;
        if (callbackAt.isPresent()) {
            callback = new CallbackTask();
            callback.setTenantId(enquiry.getTenantId());
            callback.setEnquiryId(enquiryId);
            callback.setSourceCallId(saved.getCallId());
            callback.setScheduledAt(callbackAt.get());
            callback.setAssignedTo(enquiry.getAssignedCounselorId() != null ? enquiry.getAssignedCounselorId() : counselor);
            callback.setReason(callbackReason(request.getOutcome(), unansweredStreak));
            callback = callbackRepository.save(callback);
        } else if (UNANSWERED.contains(request.getOutcome())) {
            callbackNote = unansweredStreak + " unanswered attempts in a row; no further automatic callbacks";
        }

        updateEnquiryStatus(enquiry, request.getOutcome());

        auditService.logSuccess("counseling_call:" + enquiry.getTenantId(), "LOG_COUNSELING_CALL",
                request.getDirection() + " call with " + enquiry.getApplicationNumber() + " | Outcome: " + request.getOutcome()
                        + " | Duration: " + request.getDurationSeconds() + "s"
                        + (callback != null ? " | Callback at " + callback.getScheduledAt() : ""));

        CounselingCallResponse response = mapCall(saved, enquiry);
        response.setScheduledCallback(callback != null ? mapCallback(callback, enquiry) : null);
        response.setCallbackNote(callbackNote);
        return response;
    }

    /**
     * Call history of an enquiry, newest first.
     */
    @Transactional(readOnly = true)
    public List<CounselingCallResponse> listCalls(UUID enquiryId) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        return callRepository.findByEnquiryIdOrderByCalledAtDesc(enquiryId).stream().map(c -> mapCall(c, enquiry)).toList();
    }

    /**
     * Scheduled callbacks due before the given time (default: end of tomorrow), optionally for one counselor.
     */
    @Transactional(readOnly = true)
    public List<CallbackResponse> dueCallbacks(String tenantId, String assignedTo, Instant dueBefore) {
        Instant before = dueBefore != null ? dueBefore
                : LocalDate.now(SCHOOL_ZONE).plusDays(2).atStartOfDay(SCHOOL_ZONE).toInstant();
        List<CallbackTask> tasks = assignedTo != null && !assignedTo.isBlank()
                ? callbackRepository.findByTenantIdAndAssignedToAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
                tenantId, assignedTo, CallbackStatus.SCHEDULED, before)
                : callbackRepository.findByTenantIdAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(tenantId, CallbackStatus.SCHEDULED, before);
        Set<UUID> ids = tasks.stream().map(CallbackTask::getEnquiryId).collect(Collectors.toSet());
        Map<UUID, AdmissionEnquiry> enquiries = ids.isEmpty() ? Map.of() : admissionEnquiryRepository.findByEnquiryIdIn(ids).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        return tasks.stream().map(t -> mapCallback(t, enquiries.get(t.getEnquiryId()))).toList();
    }

    /**
     * Cancels a scheduled callback.
     */
    @Transactional
    public CallbackResponse cancelCallback(UUID callbackId, CancelCallbackRequest request) {
        CallbackTask task = callbackRepository.findByCallbackId(callbackId)
                .orElseThrow(() -> new ResourceNotFoundException("CallbackTask", "callbackId", callbackId));
        if (task.getStatus() != CallbackStatus.SCHEDULED) {
            throw new ConflictException("Callback " + callbackId + " is already " + task.getStatus());
        }
        task.setStatus(CallbackStatus.CANCELLED);
        task.setClosedAt(Instant.now());
        task.setClosingNote(request.getReason());
        CallbackTask saved = callbackRepository.save(task);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(saved.getEnquiryId()).orElse(null);
        auditService.logSuccess("counseling_call:" + saved.getTenantId(), "CANCEL_CALLBACK",
                "Cancelled callback " + callbackId + " | Reason: " + request.getReason());
        return mapCallback(saved, enquiry);
    }

    /**
     * Callback rules:
     * CALLBACK_REQUESTED uses the family's requested time; NO_ANSWER retries after 2 hours; BUSY after 30 minutes;
     * other outcomes only when an explicit time is given. Unanswered calls stop scheduling after
     * {@value #MAX_UNANSWERED_ATTEMPTS} attempts in a row. Automatic times outside 09:00-19:00 IST move to 10:00
     * the next day; a time requested by the family is kept as is.
     */
    static Optional<Instant> computeCallback(CallOutcome outcome, Instant calledAt, Instant requested, int unansweredStreak) {
        if (requested != null && outcome != CallOutcome.NO_ANSWER && outcome != CallOutcome.BUSY) {
            return Optional.of(requested);
        }
        if (UNANSWERED.contains(outcome)) {
            if (unansweredStreak >= MAX_UNANSWERED_ATTEMPTS) {
                return Optional.empty();
            }
            Duration delay = outcome == CallOutcome.NO_ANSWER ? Duration.ofHours(2) : Duration.ofMinutes(30);
            return Optional.of(withinCallingHours(calledAt.plus(delay)));
        }
        return Optional.empty();
    }

    static Instant withinCallingHours(Instant candidate) {
        ZonedDateTime local = candidate.atZone(SCHOOL_ZONE);
        if (local.toLocalTime().isBefore(CALLING_STARTS)) {
            return local.toLocalDate().atTime(NEXT_DAY_SLOT).atZone(SCHOOL_ZONE).toInstant();
        }
        if (!local.toLocalTime().isBefore(CALLING_ENDS)) {
            return local.toLocalDate().plusDays(1).atTime(NEXT_DAY_SLOT).atZone(SCHOOL_ZONE).toInstant();
        }
        return candidate;
    }

    /** Number of consecutive NO_ANSWER/BUSY calls at the head of a newest-first call list. */
    static int unansweredStreak(List<CounselingCall> newestFirst) {
        int streak = 0;
        for (CounselingCall c : newestFirst) {
            if (!UNANSWERED.contains(c.getOutcome())) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private String callbackReason(CallOutcome outcome, int streak) {
        return switch (outcome) {
            case NO_ANSWER -> "Retry after no answer (attempt " + streak + " of " + MAX_UNANSWERED_ATTEMPTS + ")";
            case BUSY -> "Retry after busy line (attempt " + streak + " of " + MAX_UNANSWERED_ATTEMPTS + ")";
            case CALLBACK_REQUESTED -> "Family requested a callback";
            default -> "Follow-up call";
        };
    }

    private void updateEnquiryStatus(AdmissionEnquiry enquiry, CallOutcome outcome) {
        String status = enquiry.getEnquiryStatus();
        if ((outcome == CallOutcome.CONNECTED || outcome == CallOutcome.CALLBACK_REQUESTED)
                && EnquiryStatus.NEW_INQUIRY.name().equals(status)) {
            enquiry.setEnquiryStatus(EnquiryStatus.CONTACTED.name());
            admissionEnquiryRepository.save(enquiry);
        } else if (outcome == CallOutcome.NOT_INTERESTED
                && (EnquiryStatus.NEW_INQUIRY.name().equals(status) || EnquiryStatus.CONTACTED.name().equals(status))) {
            enquiry.setEnquiryStatus(EnquiryStatus.WITHDRAWN.name());
            admissionEnquiryRepository.save(enquiry);
        }
    }

    private CounselingCallResponse mapCall(CounselingCall c, AdmissionEnquiry e) {
        return CounselingCallResponse.builder()
                .callId(c.getCallId())
                .enquiryId(c.getEnquiryId())
                .applicationNumber(e != null ? e.getApplicationNumber() : null)
                .direction(c.getDirection().name())
                .outcome(c.getOutcome().name())
                .durationSeconds(c.getDurationSeconds())
                .notes(c.getNotes())
                .calledAt(c.getCalledAt())
                .counselorId(c.getCounselorId())
                .enquiryStatus(e != null ? e.getEnquiryStatus() : null)
                .build();
    }

    private CallbackResponse mapCallback(CallbackTask t, AdmissionEnquiry e) {
        return CallbackResponse.builder()
                .callbackId(t.getCallbackId())
                .enquiryId(t.getEnquiryId())
                .applicationNumber(e != null ? e.getApplicationNumber() : null)
                .candidateName(e != null ? e.getCandidateFirstName() + " " + e.getCandidateLastName() : null)
                .guardianName(e != null ? e.getGuardianName() : null)
                .guardianPhone(e != null ? e.getGuardianPhone() : null)
                .scheduledAt(t.getScheduledAt())
                .overdue(t.getStatus() == CallbackStatus.SCHEDULED && t.getScheduledAt().isBefore(Instant.now()))
                .assignedTo(t.getAssignedTo())
                .reason(t.getReason())
                .status(t.getStatus().name())
                .closedAt(t.getClosedAt())
                .closingNote(t.getClosingNote())
                .build();
    }
}
