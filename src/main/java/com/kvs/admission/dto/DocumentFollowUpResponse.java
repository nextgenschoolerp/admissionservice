package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentFollowUpResponse {
    private UUID followUpId;
    private UUID enquiryId;
    private String applicationNumber;
    private String candidateName;
    private String guardianName;
    private String guardianPhone;
    private String enquiryStatus;
    private List<String> outstandingDocuments;
    private String issueSummary;
    private LocalDate followUpDueDate;
    private boolean overdue;
    private String status;
    private String flaggedBy;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNotes;
    private Instant createdAt;
}
