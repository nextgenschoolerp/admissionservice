package com.kvs.admission.service;

import com.kvs.admission.dto.DocumentFollowUpResponse;
import com.kvs.admission.dto.DocumentFollowUpScanResponse;
import com.kvs.admission.dto.ResolveFollowUpRequest;
import com.kvs.admission.dto.ScanDocumentFollowUpsRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.entity.DocumentFollowUp;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.FollowUpStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.ApplicantDocumentRepository;
import com.kvs.admission.repo.DocumentFollowUpRepository;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service flagging applicants whose mandatory documents are incomplete.
 * Covers user stories:
 * US-02011, US-02051, US-02091: Flag incomplete applicant document submissions for follow-up verification (Registrar)
 */
@Service
@Slf4j
public class DocumentFollowUpService {

    static final int DEFAULT_FOLLOW_UP_DAYS = 3;

    /** Pipeline stages in which documents are expected to be in progress. */
    static final Set<String> SCANNED_STATUSES = Set.of(
            EnquiryStatus.EVALUATION_SCHEDULED.name(),
            EnquiryStatus.EVALUATION_PASSED.name(),
            EnquiryStatus.OFFER_ISSUED.name());

    @Autowired
    private DocumentFollowUpRepository followUpRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private ApplicantDocumentRepository documentRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Scans every active applicant of a branch. Applicants with a mandatory document that is missing,
     * awaiting verification or rejected get an OPEN follow-up (one per applicant, refreshed on each scan);
     * open follow-ups whose documents are now all verified are resolved automatically.
     *
     * @param request the branch to scan
     * @return scan summary with all follow-ups still open
     */
    @Transactional
    public DocumentFollowUpScanResponse scan(ScanDocumentFollowUpsRequest request) {
        int followUpDays = request.getFollowUpDays() != null ? request.getFollowUpDays() : DEFAULT_FOLLOW_UP_DAYS;
        log.info("Scanning document completeness for tenant: {}, branch: {}", request.getTenantId(), request.getBranchId());

        List<AdmissionEnquiry> applicants = admissionEnquiryRepository.findByTenantIdAndBranchIdAndEnquiryStatusInAndArchivedAtIsNull(
                request.getTenantId(), request.getBranchId(), SCANNED_STATUSES);
        Set<UUID> ids = applicants.stream().map(AdmissionEnquiry::getEnquiryId).collect(Collectors.toSet());
        Map<UUID, List<ApplicantDocument>> documents = ids.isEmpty() ? Map.of()
                : documentRepository.findByEnquiryIdIn(ids).stream().collect(Collectors.groupingBy(ApplicantDocument::getEnquiryId));
        Map<UUID, DocumentFollowUp> open = ids.isEmpty() ? new HashMap<>()
                : followUpRepository.findByEnquiryIdInAndStatus(ids, FollowUpStatus.OPEN).stream()
                .collect(Collectors.toMap(DocumentFollowUp::getEnquiryId, Function.identity()));

        String actor = SecurityUtils.getCurrentUserEmail().orElse("registrar");
        int created = 0;
        int updated = 0;
        int resolved = 0;
        for (AdmissionEnquiry applicant : applicants) {
            Map<DocumentType, String> issues = assessMandatoryDocuments(documents.getOrDefault(applicant.getEnquiryId(), List.of()));
            DocumentFollowUp existing = open.get(applicant.getEnquiryId());
            if (issues.isEmpty()) {
                if (existing != null) {
                    existing.setStatus(FollowUpStatus.RESOLVED);
                    existing.setResolvedAt(Instant.now());
                    existing.setResolvedBy(actor);
                    existing.setResolutionNotes("All mandatory documents verified");
                    followUpRepository.save(existing);
                    open.remove(applicant.getEnquiryId());
                    resolved++;
                }
                continue;
            }
            DocumentFollowUp followUp = existing != null ? existing : new DocumentFollowUp();
            if (existing == null) {
                followUp.setTenantId(applicant.getTenantId());
                followUp.setBranchId(applicant.getBranchId());
                followUp.setEnquiryId(applicant.getEnquiryId());
                followUp.setFlaggedBy(actor);
                followUp.setFollowUpDueDate(LocalDate.now().plusDays(followUpDays));
                created++;
            } else {
                updated++;
            }
            followUp.setOutstandingDocuments(issues.keySet().stream().map(Enum::name).collect(Collectors.joining(",")));
            followUp.setIssueSummary(issues.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("; ")));
            open.put(applicant.getEnquiryId(), followUpRepository.save(followUp));
        }

        Map<UUID, AdmissionEnquiry> byId = applicants.stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
        List<DocumentFollowUpResponse> openResponses = open.values().stream()
                .sorted(Comparator.comparing(DocumentFollowUp::getFollowUpDueDate))
                .map(f -> mapToResponse(f, byId.get(f.getEnquiryId())))
                .toList();

        log.info("Document scan done: scanned={}, flagged={}, updated={}, resolved={}", applicants.size(), created, updated, resolved);
        auditService.logSuccess("document_follow_up:" + request.getTenantId(), "SCAN_DOCUMENT_FOLLOW_UPS",
                "Branch: " + request.getBranchId() + " | Scanned: " + applicants.size() + " | New flags: " + created
                        + " | Updated: " + updated + " | Auto-resolved: " + resolved);

        return DocumentFollowUpScanResponse.builder()
                .tenantId(request.getTenantId())
                .branchId(request.getBranchId())
                .applicantsScanned(applicants.size())
                .newlyFlagged(created)
                .updated(updated)
                .autoResolved(resolved)
                .openFollowUps(openResponses)
                .build();
    }

    /**
     * Lists follow-ups of a branch, optionally filtered by status, earliest due date first.
     */
    @Transactional(readOnly = true)
    public List<DocumentFollowUpResponse> list(String tenantId, String branchId, FollowUpStatus status) {
        List<DocumentFollowUp> followUps = status != null
                ? followUpRepository.findByTenantIdAndBranchIdAndStatusOrderByFollowUpDueDateAsc(tenantId, branchId, status)
                : followUpRepository.findByTenantIdAndBranchIdOrderByFollowUpDueDateAsc(tenantId, branchId);
        Map<UUID, AdmissionEnquiry> enquiries = loadEnquiries(followUps);
        return followUps.stream().map(f -> mapToResponse(f, enquiries.get(f.getEnquiryId()))).toList();
    }

    /**
     * Closes an open follow-up manually, e.g. when the principal accepts an alternative document.
     */
    @Transactional
    public DocumentFollowUpResponse resolve(UUID followUpId, ResolveFollowUpRequest request) {
        DocumentFollowUp followUp = followUpRepository.findByFollowUpId(followUpId)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentFollowUp", "followUpId", followUpId));
        if (followUp.getStatus() != FollowUpStatus.OPEN) {
            throw new ConflictException("Follow-up " + followUpId + " is already " + followUp.getStatus());
        }
        followUp.setStatus(FollowUpStatus.RESOLVED);
        followUp.setResolvedAt(Instant.now());
        followUp.setResolvedBy(SecurityUtils.getCurrentUserEmail().orElse("registrar"));
        followUp.setResolutionNotes(request.getResolutionNotes());
        DocumentFollowUp saved = followUpRepository.save(followUp);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(saved.getEnquiryId()).orElse(null);
        auditService.logSuccess("document_follow_up:" + saved.getTenantId(), "RESOLVE_DOCUMENT_FOLLOW_UP",
                "Resolved follow-up " + followUpId + (enquiry != null ? " for " + enquiry.getApplicationNumber() : "")
                        + " | Notes: " + request.getResolutionNotes());
        return mapToResponse(saved, enquiry);
    }

    /**
     * Returns one issue per mandatory document type that is not VERIFIED, in enum order.
     */
    static Map<DocumentType, String> assessMandatoryDocuments(List<ApplicantDocument> documents) {
        Map<DocumentType, ApplicantDocument> byType = new EnumMap<>(DocumentType.class);
        documents.forEach(d -> byType.put(d.getDocumentType(), d));
        Map<DocumentType, String> issues = new LinkedHashMap<>();
        for (DocumentType type : DocumentType.values()) {
            if (!type.isMandatory()) {
                continue;
            }
            ApplicantDocument doc = byType.get(type);
            if (doc == null) {
                issues.put(type, "not uploaded");
            } else if (doc.getVerificationStatus() == DocumentVerificationStatus.PENDING) {
                issues.put(type, "uploaded, awaiting verification");
            } else if (doc.getVerificationStatus() == DocumentVerificationStatus.REJECTED) {
                issues.put(type, "rejected" + (doc.getVerificationNotes() != null ? " - " + doc.getVerificationNotes() : ""));
            }
        }
        return issues;
    }

    private Map<UUID, AdmissionEnquiry> loadEnquiries(List<DocumentFollowUp> followUps) {
        Set<UUID> ids = followUps.stream().map(DocumentFollowUp::getEnquiryId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : admissionEnquiryRepository.findByEnquiryIdIn(ids).stream()
                .collect(Collectors.toMap(AdmissionEnquiry::getEnquiryId, Function.identity()));
    }

    private DocumentFollowUpResponse mapToResponse(DocumentFollowUp f, AdmissionEnquiry e) {
        return DocumentFollowUpResponse.builder()
                .followUpId(f.getFollowUpId())
                .enquiryId(f.getEnquiryId())
                .applicationNumber(e != null ? e.getApplicationNumber() : null)
                .candidateName(e != null ? e.getCandidateFirstName() + " " + e.getCandidateLastName() : null)
                .guardianName(e != null ? e.getGuardianName() : null)
                .guardianPhone(e != null ? e.getGuardianPhone() : null)
                .enquiryStatus(e != null ? e.getEnquiryStatus() : null)
                .outstandingDocuments(Arrays.asList(f.getOutstandingDocuments().split(",")))
                .issueSummary(f.getIssueSummary())
                .followUpDueDate(f.getFollowUpDueDate())
                .overdue(f.getStatus() == FollowUpStatus.OPEN && f.getFollowUpDueDate().isBefore(LocalDate.now()))
                .status(f.getStatus().name())
                .flaggedBy(f.getFlaggedBy())
                .resolvedAt(f.getResolvedAt())
                .resolvedBy(f.getResolvedBy())
                .resolutionNotes(f.getResolutionNotes())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
