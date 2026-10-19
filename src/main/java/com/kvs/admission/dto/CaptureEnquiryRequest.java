package com.kvs.admission.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for capturing a walk-in inquiry lead.
 * US-02001: Capture walk-in inquiry lead details directly into the CRM database
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaptureEnquiryRequest {

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Branch ID is required")
    private String branchId;

    @NotBlank(message = "Candidate first name is required")
    @Size(max = 100, message = "Candidate first name must not exceed 100 characters")
    private String candidateFirstName;

    @NotBlank(message = "Candidate last name is required")
    @Size(max = 100, message = "Candidate last name must not exceed 100 characters")
    private String candidateLastName;

    @NotBlank(message = "Target grade ID is required")
    private String targetGradeId;

    @NotBlank(message = "Academic year ID is required")
    private String academicYearId;

    @NotBlank(message = "Guardian name is required")
    @Size(max = 150, message = "Guardian name must not exceed 150 characters")
    private String guardianName;

    @NotBlank(message = "Guardian email is required")
    @Email(message = "Invalid guardian email format")
    @Size(max = 150, message = "Guardian email must not exceed 150 characters")
    private String guardianEmail;

    @NotBlank(message = "Guardian phone is required")
    @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "Invalid phone number format")
    private String guardianPhone;

    @Size(max = 50, message = "Referral source must not exceed 50 characters")
    private String referralSource;

    private String assignedCounselorId;

    @Size(max = 2000, message = "Remarks must not exceed 2000 characters")
    private String remarks;

    /** Optional marketing campaign the lead came from (US-02019); must be an existing campaign of the tenant. */
    @Size(max = 50, message = "Campaign code must not exceed 50 characters")
    private String campaignCode;

    /** Optional; when given, enrolment (US-02005) uses it instead of asking again */
    @Past(message = "Candidate date of birth must be in the past")
    private java.time.LocalDate candidateDateOfBirth;

    private com.kvs.admission.enums.Gender candidateGender;

    /** How the guardian is related to the candidate, e.g. Mother, Father, Guardian */
    @Size(max = 30, message = "Guardian relationship must not exceed 30 characters")
    private String guardianRelationship;

    /** The fields of the original request (no date of birth, gender or relationship). */
    public CaptureEnquiryRequest(String tenantId, String branchId, String candidateFirstName, String candidateLastName,
                                 String targetGradeId, String academicYearId, String guardianName, String guardianEmail,
                                 String guardianPhone, String referralSource, String assignedCounselorId, String remarks,
                                 String campaignCode) {
        this(tenantId, branchId, candidateFirstName, candidateLastName, targetGradeId, academicYearId, guardianName,
                guardianEmail, guardianPhone, referralSource, assignedCounselorId, remarks, campaignCode, null, null, null);
    }
}
