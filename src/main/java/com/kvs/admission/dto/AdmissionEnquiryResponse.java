package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for admission enquiry details.
 * US-02001: Capture walk-in inquiry lead details directly into the CRM database
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdmissionEnquiryResponse {
    private Long id;
    private UUID enquiryId;
    private String tenantId;
    private String branchId;
    private String applicationNumber;
    private String candidateFirstName;
    private String candidateLastName;
    private String targetGradeId;
    private String academicYearId;
    private java.time.LocalDate candidateDateOfBirth;
    private String candidateGender;
    private String guardianName;
    private String guardianEmail;
    private String guardianPhone;
    private String guardianRelationship;
    private String referralSource;
    private String enquiryStatus;
    private String assignedCounselorId;
    private String remarks;
    private String campaignCode;
    private Instant archivedAt;
    private Instant createdAt;
}
