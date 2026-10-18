package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TcVerificationResponse {
    private UUID verificationId;
    private UUID enquiryId;
    private String applicationNumber;
    private UUID documentId;
    private String board;
    private String tcNumber;
    private String previousSchoolName;
    private String result;
    private String boardReference;
    private LocalDate issueDate;
    private String responseMessage;
    /** Vault status of the transfer certificate after the check (VERIFIED, REJECTED or still PENDING). */
    private String documentVerificationStatus;
    private String checkedBy;
    private Instant checkedAt;
}
