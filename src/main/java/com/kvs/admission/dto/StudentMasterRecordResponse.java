package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for an active Student Master record.
 * US-02005: Convert verified candidate leads into active Student Master records
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentMasterRecordResponse {
    private UUID studentId;
    private String admissionNumber;
    private String tenantId;
    private String branchId;
    private UUID enquiryId;
    private String applicationNumber;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String gradeId;
    private String academicYearId;
    private String guardianName;
    private String guardianEmail;
    private String guardianPhone;
    private String status;
    private String sectionCode;
    private String convertedBy;
    private Instant createdAt;
}
