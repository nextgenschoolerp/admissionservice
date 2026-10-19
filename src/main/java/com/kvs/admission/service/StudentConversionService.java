package com.kvs.admission.service;

import com.kvs.admission.dto.ConvertLeadRequest;
import com.kvs.admission.dto.StudentMasterRecordResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.AdmissionOffer;
import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.entity.StudentMasterRecord;
import com.kvs.admission.enums.DocumentType;
import com.kvs.admission.enums.DocumentVerificationStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.OfferStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.AdmissionOfferRepository;
import com.kvs.admission.repo.ApplicantDocumentRepository;
import com.kvs.admission.repo.StudentMasterRecordRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service converting verified candidate leads into active Student Master records.
 * Covers user story:
 * US-02005: Convert verified candidate leads into active Student Master records
 * Actor: Applicant Parent
 */
@Service
@Slf4j
public class StudentConversionService {

    private static final Set<String> CONVERTIBLE_STATUSES = Set.of(
            EnquiryStatus.EVALUATION_PASSED.name(),
            EnquiryStatus.OFFER_ISSUED.name());

    @Autowired
    private StudentMasterRecordRepository studentRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private ApplicantDocumentRepository documentRepository;

    @Autowired
    private AdmissionOfferRepository offerRepository;

    @Autowired
    private AdmissionAuditService auditService;

    @Autowired
    private AdmissionAccessGuard accessGuard;

    /**
     * Converts a verified lead into an active Student Master record and marks the enquiry ENROLLED.
     * Preconditions: the candidate passed evaluation, every mandatory document is VERIFIED, and if an
     * offer was issued its seat reservation deposit has been paid (offer ACCEPTED).
     * A parent may only convert a lead for which they are the registered guardian.
     *
     * @param enquiryId the enquiry UUID
     * @param request   student details not captured at enquiry time
     * @return the created student record
     */
    @Transactional
    public StudentMasterRecordResponse convertLead(UUID enquiryId, ConvertLeadRequest request) {
        log.info("Converting lead {} into a Student Master record", enquiryId);
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", enquiryId));
        assertCallerMayActOn(enquiry);

        if (studentRepository.existsByEnquiryId(enquiryId)
                || EnquiryStatus.ENROLLED.name().equals(enquiry.getEnquiryStatus())) {
            throw new ConflictException("Enquiry " + enquiry.getApplicationNumber()
                    + " has already been converted into a student record");
        }
        if (!CONVERTIBLE_STATUSES.contains(enquiry.getEnquiryStatus())) {
            throw new BadRequestException("Enquiry " + enquiry.getApplicationNumber() + " in status "
                    + enquiry.getEnquiryStatus() + " is not ready for conversion; the candidate must pass evaluation");
        }

        Map<DocumentType, ApplicantDocument> documents = documentRepository.findByEnquiryIdOrderByDocumentTypeAsc(enquiryId)
                .stream()
                .collect(Collectors.toMap(ApplicantDocument::getDocumentType, d -> d));
        List<DocumentType> unverified = Arrays.stream(DocumentType.values())
                .filter(DocumentType::isMandatory)
                .filter(type -> documents.get(type) == null
                        || documents.get(type).getVerificationStatus() != DocumentVerificationStatus.VERIFIED)
                .toList();
        if (!unverified.isEmpty()) {
            throw new BadRequestException("Mandatory documents are missing or not yet verified: " + unverified);
        }

        // Date of birth and gender come from the application unless the registrar gives them now
        java.time.LocalDate dateOfBirth = request.getDateOfBirth() != null ? request.getDateOfBirth() : enquiry.getCandidateDateOfBirth();
        com.kvs.admission.enums.Gender gender = request.getGender() != null ? request.getGender() : enquiry.getCandidateGender();
        if (dateOfBirth == null || gender == null) {
            throw new BadRequestException((dateOfBirth == null ? "Date of birth" : "Gender") + " is required: application "
                    + enquiry.getApplicationNumber() + " does not record it");
        }

        String gradeId = enquiry.getTargetGradeId();
        List<AdmissionOffer> offers = offerRepository.findByEnquiryIdOrderByCreatedAtDesc(enquiryId);
        if (!offers.isEmpty()) {
            AdmissionOffer accepted = offers.stream()
                    .filter(o -> o.getOfferStatus() == OfferStatus.ACCEPTED)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("The seat reservation deposit for application "
                            + enquiry.getApplicationNumber() + " has not been paid"));
            gradeId = accepted.getOfferedGradeId();
        }

        StudentMasterRecord student = new StudentMasterRecord();
        student.setTenantId(enquiry.getTenantId());
        student.setBranchId(enquiry.getBranchId());
        student.setEnquiryId(enquiryId);
        student.setAdmissionNumber(ReferenceNumberGenerator.next("ADM"));
        student.setFirstName(enquiry.getCandidateFirstName());
        student.setLastName(enquiry.getCandidateLastName());
        student.setDateOfBirth(dateOfBirth);
        student.setGender(gender);
        student.setGradeId(gradeId);
        student.setAcademicYearId(enquiry.getAcademicYearId());
        student.setGuardianName(enquiry.getGuardianName());
        student.setGuardianEmail(enquiry.getGuardianEmail());
        student.setGuardianPhone(enquiry.getGuardianPhone());
        student.setStatus("ACTIVE");
        student.setConvertedBy(SecurityUtils.getCurrentUserEmail().orElse("applicant_parent"));
        StudentMasterRecord saved = studentRepository.save(student);

        enquiry.setCandidateDateOfBirth(dateOfBirth);
        enquiry.setCandidateGender(gender);
        enquiry.setEnquiryStatus(EnquiryStatus.ENROLLED.name());
        admissionEnquiryRepository.save(enquiry);

        log.info("Converted application {} into student {} ({})", enquiry.getApplicationNumber(),
                saved.getAdmissionNumber(), saved.getStudentId());
        auditService.logSuccess("student_master:" + enquiry.getTenantId(), "CONVERT_LEAD_TO_STUDENT",
                "Converted application " + enquiry.getApplicationNumber()
                        + " into admission " + saved.getAdmissionNumber()
                        + " | Student: " + saved.getStudentId()
                        + " | Grade: " + gradeId);

        return mapToResponse(saved, enquiry);
    }

    /**
     * Retrieves a Student Master record. Parents may only view their own child's record.
     *
     * @param studentId the student UUID
     * @return the student record
     */
    @Transactional(readOnly = true)
    public StudentMasterRecordResponse getStudent(UUID studentId) {
        log.info("Fetching Student Master record: {}", studentId);
        StudentMasterRecord student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentMasterRecord", "studentId", studentId));
        AdmissionEnquiry enquiry = admissionEnquiryRepository.findByEnquiryId(student.getEnquiryId()).orElse(null);
        accessGuard.requireStaffOrGuardian(student.getGuardianEmail(),
                "You may only view student records of your own children");
        return mapToResponse(student, enquiry);
    }

    private void assertCallerMayActOn(AdmissionEnquiry enquiry) {
        accessGuard.requireStaffOrGuardian(enquiry.getGuardianEmail(),
                "You may only convert admission leads registered under your guardian email");
    }

    private StudentMasterRecordResponse mapToResponse(StudentMasterRecord student, AdmissionEnquiry enquiry) {
        return StudentMasterRecordResponse.builder()
                .studentId(student.getStudentId())
                .admissionNumber(student.getAdmissionNumber())
                .tenantId(student.getTenantId())
                .branchId(student.getBranchId())
                .enquiryId(student.getEnquiryId())
                .applicationNumber(enquiry != null ? enquiry.getApplicationNumber() : null)
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .dateOfBirth(student.getDateOfBirth())
                .gender(student.getGender().name())
                .gradeId(student.getGradeId())
                .academicYearId(student.getAcademicYearId())
                .guardianName(student.getGuardianName())
                .guardianEmail(student.getGuardianEmail())
                .guardianPhone(student.getGuardianPhone())
                .status(student.getStatus())
                .sectionCode(student.getSectionCode())
                .convertedBy(student.getConvertedBy())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
