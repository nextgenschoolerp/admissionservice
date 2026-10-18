package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Outcome of a waitlist escalation run (US-02014).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WaitlistEscalationResponse {
    private String gradeId;
    /** Offers that lapsed and released their seat during this run. */
    private int expiredOffers;
    private int freeSeatsBefore;
    private int offersIssued;
    /** Entries dropped because the candidate is no longer eligible (e.g. withdrew). */
    private int removedIneligible;
    private int stillWaiting;
    private List<AdmissionOfferResponse> issuedOffers;
}
