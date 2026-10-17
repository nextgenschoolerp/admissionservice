package com.kvs.admission.service;

import com.kvs.admission.dto.AdmissionEnquiryResponse;
import com.kvs.admission.dto.CaptureEnquiryRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AuditLog;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AuditLogRepository;
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
 * Actor: Front-Office Counselor
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
        enquiry.setReferralSource(request.getReferralSource() != null ? request.getReferralSource() : "WALK_IN");
        enquiry.setEnquiryStatus("NEW_INQUIRY");
        enquiry.setAssignedCounselorId(request.getAssignedCounselorId());
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
        return mapToResponse(enquiry);
    }

    /**
     * Lists all enquiries for a given tenant and branch.
     *
     * @param tenantId the tenant identifier
     * @param branchId the branch identifier
     * @return list of admission enquiry responses
     */
    public List<AdmissionEnquiryResponse> listEnquiries(String tenantId, String branchId) {
        log.info("Listing enquiries for tenantId: {}, branchId: {}", tenantId, branchId);
        return admissionEnquiryRepository.findByTenantIdAndBranchId(tenantId, branchId).stream()
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
                .guardianName(entity.getGuardianName())
                .guardianEmail(entity.getGuardianEmail())
                .guardianPhone(entity.getGuardianPhone())
                .referralSource(entity.getReferralSource())
                .enquiryStatus(entity.getEnquiryStatus())
                .assignedCounselorId(entity.getAssignedCounselorId())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
