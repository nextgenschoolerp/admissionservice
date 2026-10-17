package com.kvs.admission.controller;

import com.kvs.admission.dto.CollectDepositRequest;
import com.kvs.admission.dto.DepositReceiptResponse;
import com.kvs.admission.enums.PaymentMode;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AdmissionFeeDepositController}.
 * Covers: US-02008: Collect seat reservation deposit fees and issue digital receipts
 * Actor: School Principal
 */
class AdmissionFeeDepositControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID OFFER_ID = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/admissions/offers/" + OFFER_ID + "/deposits";

    private final CollectDepositRequest validRequest =
            new CollectDepositRequest(new BigDecimal("15000.00"), PaymentMode.UPI, "UPI-REF-778899", null);

    @Test
    @DisplayName("POST deposits - Principal collects deposit and receives digital receipt (201)")
    void collectDeposit_Success() throws Exception {
        DepositReceiptResponse receipt = DepositReceiptResponse.builder()
                .receiptNumber("RCPT-2026-ABCD1234")
                .offerId(OFFER_ID)
                .offerStatus("ACCEPTED")
                .amountPaid(new BigDecimal("15000.00"))
                .currency("INR")
                .paymentMode("UPI")
                .transactionReference("UPI-REF-778899")
                .build();
        when(depositService.collectDeposit(eq(OFFER_ID), any(CollectDepositRequest.class))).thenReturn(receipt);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .header("X-Trace-Id", "test-trace-02008")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02008"))
                .andExpect(jsonPath("$.data.receiptNumber").value("RCPT-2026-ABCD1234"))
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST deposits - Missing amount and mode returns 400")
    void collectDeposit_ValidationFailure() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CollectDepositRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST deposits - Amount different from offer fee returns 400")
    void collectDeposit_WrongAmount() throws Exception {
        when(depositService.collectDeposit(eq(OFFER_ID), any(CollectDepositRequest.class)))
                .thenThrow(new BadRequestException("Amount paid must equal the seat reservation fee"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST deposits - No token returns 401")
    void collectDeposit_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST deposits - Applicant Parent is forbidden (403)")
    void collectDeposit_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("APPLICANT_PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST deposits - Deposit already collected returns 409")
    void collectDeposit_Conflict() throws Exception {
        when(depositService.collectDeposit(eq(OFFER_ID), any(CollectDepositRequest.class)))
                .thenThrow(new ConflictException("The seat reservation deposit has already been collected"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FINANCE_CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET deposits/receipts/{receiptNumber} - Unknown receipt returns 404")
    void getReceipt_NotFound() throws Exception {
        when(depositService.getReceipt("RCPT-NOPE"))
                .thenThrow(new ResourceNotFoundException("AdmissionFeeDeposit", "receiptNumber", "RCPT-NOPE"));

        mockMvc.perform(get("/api/v1/admissions/deposits/receipts/RCPT-NOPE")
                        .header("Authorization", bearer("SCHOOL_PRINCIPAL")))
                .andExpect(status().isNotFound());
    }
}
