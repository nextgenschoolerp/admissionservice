package com.kvs.admission.service;

import com.kvs.admission.dto.AdmissionEnquiryResponse;
import com.kvs.admission.dto.CaptureEnquiryRequest;
import com.kvs.admission.dto.EnquiryDecisionRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AuditLog;
import com.kvs.admission.entity.MarketingCampaign;
import com.kvs.admission.enums.CampaignStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AuditLogRepository;
import com.kvs.admission.repo.MarketingCampaignRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing admission enquiry leads.
 * Covers user story:
 * US-02001: Capture walk-in inquiry lead details directly into the CRM database
 * Actor: Front-Office Counselor (also Applicant Parent for self-service enquiries: US-02021, US-02061)
 */
@Service
@Slf4j
public class AdmissionEnquiryService {

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private HttpServletRequest httpRequest;

    @Autowired
    private MarketingCampaignRepository campaignRepository;

    @Autowired
    private AdmissionAccessGuard accessGuard;

    /**
     * Captures a walk-in inquiry lead and persists it into the CRM database.
     * Generates a unique application number, validates for duplicate phone/email,
     * and logs an audit entry.
     *
     * @param request the walk-in enquiry capture request
     * @return the created admission enquiry response
     */
    @Transactional
    public AdmissionEnquiryResponse captureWalkInEnquiry(CaptureEnquiryRequest request) {
        log.info("Capturing walk-in enquiry lead for tenant: {}, branch: {}, candidate: {} {}",
                request.getTenantId(), request.getBranchId(),
                request.getCandidateFirstName(), request.getCandidateLastName());

        // A parent submitting their own enquiry must register it under their login email
        boolean selfService = !accessGuard.isStaff();
        accessGuard.requireStaffOrGuardian(request.getGuardianEmail(),
                "Parents may only submit enquiries with their own login email as the guardian email");

        if (request.getCampaignCode() != null && !request.getCampaignCode().isBlank()) {
            MarketingCampaign campaign = campaignRepository
                    .findByTenantIdAndCampaignCode(request.getTenantId(), request.getCampaignCode())
                    .orElseThrow(() -> new BadRequestException("Unknown campaign code " + request.getCampaignCode()));
            if (campaign.getStatus() != CampaignStatus.ACTIVE) {
                throw new BadRequestException("Campaign " + campaign.getCampaignCode() + " is " + campaign.getStatus()
                        + " and no longer accepts leads");
            }
        }

        // Check for duplicate guardian phone within the same tenant
        admissionEnquiryRepository.findByGuardianPhoneAndTenantId(request.getGuardianPhone(), request.getTenantId())
                .ifPresent(existing -> {
                    throw new ConflictException("An enquiry already exists with guardian phone "
                            + request.getGuardianPhone() + " for this tenant. Existing application: "
                            + existing.getApplicationNumber());
                });

        // Check for duplicate guardian email within the same tenant
        admissionEnquiryRepository.findByGuardianEmailAndTenantId(request.getGuardianEmail(), request.getTenantId())
                .ifPresent(existing -> {
                    throw new ConflictException("An enquiry already exists with guardian email "
                            + request.getGuardianEmail() + " for this tenant. Existing application: "
                            + existing.getApplicationNumber());
                });

        // Generate unique application number
        String applicationNumber = generateApplicationNumber();

        AdmissionEnquiry enquiry = new AdmissionEnquiry();
        enquiry.setTenantId(request.getTenantId());
        enquiry.setBranchId(request.getBranchId());
        enquiry.setApplicationNumber(applicationNumber);
        enquiry.setCandidateFirstName(request.getCandidateFirstName());
        enquiry.setCandidateLastName(request.getCandidateLastName());
        enquiry.setTargetGradeId(request.getTargetGradeId());
        enquiry.setAcademicYearId(request.getAcademicYearId());
        enquiry.setGuardianName(request.getGuardianName());
        enquiry.setGuardianEmail(request.getGuardianEmail());
        enquiry.setGuardianPhone(request.getGuardianPhone());
        enquiry.setCandidateDateOfBirth(request.getCandidateDateOfBirth());
        enquiry.setCandidateGender(request.getCandidateGender());
        enquiry.setGuardianRelationship(request.getGuardianRelationship() == null || request.getGuardianRelationship().isBlank()
                ? null : request.getGuardianRelationship().trim());
        enquiry.setReferralSource(request.getReferralSource() != null
                ? request.getReferralSource()
                : (selfService ? "PARENT_PORTAL" : "WALK_IN"));
        enquiry.setCampaignCode(request.getCampaignCode() != null && !request.getCampaignCode().isBlank()
                ? request.getCampaignCode() : null);
        enquiry.setEnquiryStatus("NEW_INQUIRY");
        // A counselor who records the enquiry owns it unless another counselor is named
        String counselor = request.getAssignedCounselorId();
        if ((counselor == null || counselor.isBlank()) && accessGuard.isCounselor()) {
            counselor = accessGuard.currentUserEmail().orElse(null);
        }
        enquiry.setAssignedCounselorId(counselor);
        enquiry.setRemarks(request.getRemarks());

        AdmissionEnquiry saved = admissionEnquiryRepository.save(enquiry);
        log.info("Successfully captured walk-in enquiry lead. Application Number: {}, ID: {}",
                saved.getApplicationNumber(), saved.getId());

        logAudit(request.getTenantId(), "CAPTURE_WALKIN_LEAD", "SUCCESS",
                "Captured walk-in enquiry for " + request.getCandidateFirstName()
                        + " " + request.getCandidateLastName()
                        + " | Application: " + applicationNumber
                        + " | Guardian: " + request.getGuardianName()
                        + " | Phone: " + request.getGuardianPhone());

        return mapToResponse(saved);
    }

    /**
     * Retrieves an admission enquiry by its UUID.
     *
     * @param enquiryId the enquiry UUID
     * @return the admission enquiry response
     */
    public AdmissionEnquiryResponse getEnquiryById(UUID enquiryId) {
        log.info("Fetching admission enquiry by enquiryId: {}", enquiryId);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        accessGuard.requireStaffOrGuardian(enquiry.getGuardianEmail(), "You may only view your own enquiries");
        return mapToResponse(enquiry);
    }

    /**
     * Closes an open application as REJECTED ("Not offered") or WITHDRAWN. The reason is appended to the
     * remarks with who decided; enrolled or already closed applications cannot be closed again (409).
     */
    @Transactional
    public AdmissionEnquiryResponse closeEnquiry(UUID enquiryId, EnquiryDecisionRequest request) {
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        String status = enquiry.getEnquiryStatus();
        if (EnquiryStatus.ENROLLED.name().equals(status) || EnquiryStatus.REJECTED.name().equals(status)
                || EnquiryStatus.WITHDRAWN.name().equals(status)) {
            throw new ConflictException("Application " + enquiry.getApplicationNumber() + " is already " + status
                    + " and cannot be closed again");
        }
        String by = accessGuard.currentUserEmail().orElse("staff");
        String label = EnquiryStatus.REJECTED.name().equals(request.getDecision()) ? "Not offered" : "Withdrawn";
        String note = label + " (" + by + "): " + request.getReason().trim();
        String remarks = enquiry.getRemarks() == null || enquiry.getRemarks().isBlank() ? note : enquiry.getRemarks() + " | " + note;
        enquiry.setRemarks(remarks.length() > 2000 ? remarks.substring(remarks.length() - 2000) : remarks);
        enquiry.setEnquiryStatus(request.getDecision());
        AdmissionEnquiry saved = admissionEnquiryRepository.save(enquiry);

        logAudit(enquiry.getTenantId(), "CLOSE_ENQUIRY_" + request.getDecision(), "SUCCESS",
                "Application " + enquiry.getApplicationNumber() + " moved from " + status + " to " + request.getDecision()
                        + " by " + by + " | Reason: " + request.getReason().trim());
        return mapToResponse(saved);
    }

    /**
     * Lists the active (not archived) enquiries for a given tenant and branch.
     *
     * @param tenantId the tenant identifier
     * @param branchId the branch identifier
     * @return list of admission enquiry responses
     */
    public List<AdmissionEnquiryResponse> listEnquiries(String tenantId, String branchId) {
        log.info("Listing enquiries for tenantId: {}, branchId: {}", tenantId, branchId);
        return admissionEnquiryRepository.findByTenantIdAndBranchIdAndArchivedAtIsNull(tenantId, branchId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private String generateApplicationNumber() {
        int year = LocalDate.now().getYear();
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "APP-" + year + "-" + uniquePart;
    }

    private void logAudit(String tenantId, String action, String outcome, String details) {
        try {
            String currentUser = SecurityUtils.getCurrentUserEmail().orElse("front_office_counselor");
            String ipAddress = (httpRequest != null && httpRequest.getRemoteAddr() != null)
                    ? httpRequest.getRemoteAddr()
                    : "127.0.0.1";

            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(currentUser);
            auditLog.setIpAddress(ipAddress);
            auditLog.setAction(action);
            auditLog.setResource("admission_enquiry:" + tenantId);
            auditLog.setOutcome(outcome);
            auditLog.setEventTime(Instant.now());
            auditLog.setDetails(details);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    private AdmissionEnquiryResponse mapToResponse(AdmissionEnquiry entity) {
        return AdmissionEnquiryResponse.builder()
                .id(entity.getId())
                .enquiryId(entity.getEnquiryId())
                .tenantId(entity.getTenantId())
                .branchId(entity.getBranchId())
                .applicationNumber(entity.getApplicationNumber())
                .candidateFirstName(entity.getCandidateFirstName())
                .candidateLastName(entity.getCandidateLastName())
                .targetGradeId(entity.getTargetGradeId())
                .academicYearId(entity.getAcademicYearId())
                .candidateDateOfBirth(entity.getCandidateDateOfBirth())
                .candidateGender(entity.getCandidateGender() == null ? null : entity.getCandidateGender().name())
                .guardianName(entity.getGuardianName())
                .guardianEmail(entity.getGuardianEmail())
                .guardianPhone(entity.getGuardianPhone())
                .guardianRelationship(entity.getGuardianRelationship())
                .referralSource(entity.getReferralSource())
                .enquiryStatus(entity.getEnquiryStatus())
                .assignedCounselorId(entity.getAssignedCounselorId())
                .remarks(entity.getRemarks())
                .campaignCode(entity.getCampaignCode())
                .archivedAt(entity.getArchivedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
