package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * A grade waitlist with its seat position (US-02014).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaitlistResponse {
    private String tenantId;
    private String branchId;
    private String academicYearId;
    private String gradeId;
    private Integer totalSeats;
    private Integer offerValidityDays;
    private BigDecimal seatDepositAmount;
    private boolean autoEscalationEnabled;
    /** Enrolled students plus live (dispatched or accepted, not yet enrolled) offers. */
    private int occupiedSeats;
    private int freeSeats;
    private List<WaitlistEntryResponse> entries;
}
