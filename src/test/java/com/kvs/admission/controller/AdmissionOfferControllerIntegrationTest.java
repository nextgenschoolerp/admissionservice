package com.kvs.admission.controller;

import com.kvs.admission.dto.AdmissionOfferResponse;
import com.kvs.admission.dto.IssueOfferRequest;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AdmissionOfferController}.
 * Covers: US-02006: Issue automated offer letters with embedded online fee payment links
 * Actor: Finance Cashier
 */
class AdmissionOfferControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/offers";
    private static final UUID ENQUIRY_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    private final IssueOfferRequest validRequest = new IssueOfferRequest(ENQUIRY_ID, null,
            LocalDate.now().plusDays(10), new BigDecimal("15000.00"));

    @Test
    @DisplayName("POST offers - Finance Cashier issues offer letter with payment link (201)")
    void issueOffer_Success() throws Exception {
        AdmissionOfferResponse response = AdmissionOfferResponse.builder()
                .offerId(UUID.randomUUID())
                .enquiryId(ENQUIRY_ID)
                .offerCode("OFFER-2026-ABCD1234")
                .offerStatus("DISPATCHED")
                .initialFeeAmount(new BigDecimal("15000.00"))
                .currency("INR")
                .paymentLinkUrl("https://pay.kvs.local/admissions/offers?offer=OFFER-2026-ABCD1234&token=abc")
                .build();
        when(offerService.issueOffer(any(IssueOfferRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FINANCE_CASHIER"))
                        .header("X-Trace-Id", "test-trace-02006")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02006"))
                .andExpect(jsonPath("$.message").value("Offer letter issued with online fee payment link"))
                .andExpect(jsonPath("$.data.offerStatus").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.paymentLinkUrl").exists());
    }

    @Test
    @DisplayName("POST offers - Zero fee and past validity returns 400")
    void issueOffer_ValidationFailure() throws Exception {
        IssueOfferRequest request = new IssueOfferRequest(ENQUIRY_ID, null, LocalDate.now().minusDays(1), BigDecimal.ZERO);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    @DisplayName("POST offers - Candidate who has not passed evaluation returns 400")
    void issueOffer_NotEvaluated() throws Exception {
        when(offerService.issueOffer(any(IssueOfferRequest.class)))
                .thenThrow(new BadRequestException("An offer can only be issued after the candidate passes evaluation"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST offers - No token returns 401")
    void issueOffer_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST offers - Front-Office Counselor is forbidden (403)")
    void issueOffer_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST offers - Active offer already exists returns 409")
    void issueOffer_Conflict() throws Exception {
        when(offerService.issueOffer(any(IssueOfferRequest.class)))
                .thenThrow(new ConflictException("Enquiry already has an active offer"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET offers/{offerId} - Unknown offer returns 404")
    void getOffer_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(offerService.getOffer(unknown)).thenThrow(new ResourceNotFoundException("AdmissionOffer", "offerId", unknown));

        mockMvc.perform(get(URL + "/" + unknown).header("Authorization", bearer("FINANCE_CASHIER")))
                .andExpect(status().isNotFound());
    }
}
