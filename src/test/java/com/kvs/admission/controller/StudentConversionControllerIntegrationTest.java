package com.kvs.admission.controller;

import com.kvs.admission.dto.ConvertLeadRequest;
import com.kvs.admission.dto.StudentMasterRecordResponse;
import com.kvs.admission.enums.Gender;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ForbiddenException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link StudentConversionController}.
 * Covers: US-02005: Convert verified candidate leads into active Student Master records
 * Actor: Applicant Parent
 */
class StudentConversionControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID ENQUIRY_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/admissions/enquiries/" + ENQUIRY_ID + "/student-conversion";

    private final ConvertLeadRequest validRequest = new ConvertLeadRequest(LocalDate.of(2014, 5, 12), Gender.MALE);

    @Test
    @DisplayName("POST student-conversion - Parent converts verified lead into student record (201)")
    void convertLead_Success() throws Exception {
        StudentMasterRecordResponse response = StudentMasterRecordResponse.builder()
                .studentId(UUID.randomUUID())
                .admissionNumber("ADM-2026-ABCD1234")
                .enquiryId(ENQUIRY_ID)
                .firstName("Rohan")
                .lastName("Mehta")
                .status("ACTIVE")
                .build();
        when(conversionService.convertLead(eq(ENQUIRY_ID), any(ConvertLeadRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("APPLICANT_PARENT"))
                        .header("X-Trace-Id", "test-trace-02005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02005"))
                .andExpect(jsonPath("$.data.admissionNumber").value("ADM-2026-ABCD1234"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST student-conversion - Future date of birth returns 400")
    void convertLead_ValidationFailure() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("APPLICANT_PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConvertLeadRequest(LocalDate.now().plusDays(1), Gender.FEMALE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST student-conversion - Unverified documents returns 400")
    void convertLead_DocumentsNotVerified() throws Exception {
        when(conversionService.convertLead(eq(ENQUIRY_ID), any(ConvertLeadRequest.class)))
                .thenThrow(new BadRequestException("Mandatory documents are missing or not yet verified: [TRANSFER_CERTIFICATE]"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("APPLICANT_PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST student-conversion - No token returns 401")
    void convertLead_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST student-conversion - Teacher role is forbidden (403)")
    void convertLead_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST student-conversion - Parent converting another family's lead returns 403")
    void convertLead_NotGuardian() throws Exception {
        when(conversionService.convertLead(eq(ENQUIRY_ID), any(ConvertLeadRequest.class)))
                .thenThrow(new ForbiddenException("You may only convert admission leads registered under your guardian email"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("APPLICANT_PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST student-conversion - Already converted returns 409")
    void convertLead_Conflict() throws Exception {
        when(conversionService.convertLead(eq(ENQUIRY_ID), any(ConvertLeadRequest.class)))
                .thenThrow(new ConflictException("Enquiry has already been converted into a student record"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST student-conversion - Unknown enquiry returns 404")
    void convertLead_NotFound() throws Exception {
        when(conversionService.convertLead(eq(ENQUIRY_ID), any(ConvertLeadRequest.class)))
                .thenThrow(new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", ENQUIRY_ID));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isNotFound());
    }
}
