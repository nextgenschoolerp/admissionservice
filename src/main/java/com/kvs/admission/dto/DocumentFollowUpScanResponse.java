package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outcome of a document follow-up scan (US-02011).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentFollowUpScanResponse {
    private String tenantId;
    private String branchId;
    private int applicantsScanned;
    private int newlyFlagged;
    private int updated;
    private int autoResolved;
    /** All follow-ups still open after the scan. */
    private List<DocumentFollowUpResponse> openFollowUps;
}
