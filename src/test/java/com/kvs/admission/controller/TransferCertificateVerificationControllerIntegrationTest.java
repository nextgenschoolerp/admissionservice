package com.kvs.admission.controller;

import com.kvs.admission.dto.TcVerificationResponse;
import com.kvs.admission.dto.VerifyTransferCertificateRequest;
import com.kvs.admission.enums.EducationBoard;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link TransferCertificateVerificationController}.
 * Covers: US-02017 — Verify transfer certificate validity against education board databases (Front-Office Counselor)
 */
class TransferCertificateVerificationControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID ENQUIRY = UUID.fromString("5eed0002-0001-4000-8000-000000000002");
    private static final String URL = "/api/v1/admissions/enquiries/" + ENQUIRY + "/transfer-certificate/verifications";

    private String body() throws Exception {
        return json(new VerifyTransferCertificateRequest(EducationBoard.CBSE, "CBSE/2730125/2026/004512", "St. Mary's Convent School"));
    }

    @Test
    @DisplayName("POST verification - Counselor checks the TC with the board (200)")
    void verify_Success() throws Exception {
        when(tcVerificationService.verify(eq(ENQUIRY), any())).thenReturn(TcVerificationResponse.builder()
                .result("VALID").boardReference("CBSE-REG-2730125-004512").documentVerificationStatus("VERIFIED").build());

        mockMvc.perform(post(URL).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("VALID"))
                .andExpect(jsonPath("$.data.documentVerificationStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("POST verification - Unknown board value and blank TC number return 400")
    void verify_ValidationFailure() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"board\":\"NIOS_XYZ\",\"tcNumber\":\"CBSE/1/2026/1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
        mockMvc.perform(post(URL).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyTransferCertificateRequest(EducationBoard.ICSE, " ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST verification - TC not uploaded returns 400; already verified returns 409")
    void verify_DomainErrors() throws Exception {
        UUID other = UUID.randomUUID();
        when(tcVerificationService.verify(eq(ENQUIRY), any())).thenThrow(new ConflictException("Already verified"));
        when(tcVerificationService.verify(eq(other), any())).thenThrow(new BadRequestException("Upload the transfer certificate first"));

        mockMvc.perform(post(URL).header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admissions/enquiries/" + other + "/transfer-certificate/verifications")
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR")).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST verification - Applicant Parent 403; no token 401")
    void verify_Security() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", bearer("APPLICANT_PARENT")).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
    }
}
