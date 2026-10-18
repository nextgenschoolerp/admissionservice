package com.kvs.admission.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.admission.dto.AdmissionEnquiryResponse;
import com.kvs.admission.dto.CaptureEnquiryRequest;
import com.kvs.admission.service.AdmissionEnquiryService;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AdmissionEnquiryController}.
 * Covers: US-02001: Capture walk-in inquiry lead details directly into the CRM database
 * Actor: Front-Office Counselor
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdmissionEnquiryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    private AdmissionEnquiryService admissionEnquiryService;

    private String counselorToken;
    private String adminToken;
    private String teacherToken;

    private static final UUID ENQUIRY_UUID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        counselorToken = jwtTokenService.generateToken("counselor@kvs.com",
                Map.of("roles", List.of("ROLE_FRONT_OFFICE_COUNSELOR")));
        adminToken = jwtTokenService.generateToken("admin@kvs.com",
                Map.of("roles", List.of("ROLE_SUPER_ADMIN")));
        teacherToken = jwtTokenService.generateToken("teacher@kvs.com",
                Map.of("roles", List.of("ROLE_TEACHER")));
    }

    // --- Success Cases ---

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Front-Office Counselor captures walk-in lead returns 201 Created")
    void testCaptureEnquiry_Success() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "sanjay.m@example.com", "+919876543210",
                "WALK_IN", null, "Walk-in inquiry for Grade 10", null
        );

        AdmissionEnquiryResponse response = AdmissionEnquiryResponse.builder()
                .id(1L)
                .enquiryId(ENQUIRY_UUID)
                .tenantId("tenant-001")
                .branchId("branch-001")
                .applicationNumber("APP-2026-ABCD1234")
                .candidateFirstName("Rohan")
                .candidateLastName("Mehta")
                .targetGradeId("grade-10-id")
                .academicYearId("ay-2026-id")
                .guardianName("Sanjay Mehta")
                .guardianEmail("sanjay.m@example.com")
                .guardianPhone("+919876543210")
                .referralSource("WALK_IN")
                .enquiryStatus("NEW_INQUIRY")
                .remarks("Walk-in inquiry for Grade 10")
                .createdAt(Instant.now())
                .build();

        when(admissionEnquiryService.captureWalkInEnquiry(any(CaptureEnquiryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + counselorToken)
                        .header("X-Trace-Id", "test-trace-02001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02001"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Walk-in inquiry lead captured successfully into the CRM database"))
                .andExpect(jsonPath("$.data.applicationNumber").value("APP-2026-ABCD1234"))
                .andExpect(jsonPath("$.data.candidateFirstName").value("Rohan"))
                .andExpect(jsonPath("$.data.candidateLastName").value("Mehta"))
                .andExpect(jsonPath("$.data.enquiryStatus").value("NEW_INQUIRY"))
                .andExpect(jsonPath("$.data.referralSource").value("WALK_IN"))
                .andExpect(jsonPath("$.data.guardianEmail").value("sanjay.m@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Super Admin can also capture leads returns 201 Created")
    void testCaptureEnquiry_SuperAdmin_Success() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Priya", "Sharma",
                "grade-8-id", "ay-2026-id",
                "Rajesh Sharma", "rajesh.s@example.com", "+919876500000",
                "PHONE", null, null, null
        );

        AdmissionEnquiryResponse response = AdmissionEnquiryResponse.builder()
                .id(2L)
                .enquiryId(UUID.randomUUID())
                .tenantId("tenant-001")
                .branchId("branch-001")
                .applicationNumber("APP-2026-EFGH5678")
                .candidateFirstName("Priya")
                .candidateLastName("Sharma")
                .enquiryStatus("NEW_INQUIRY")
                .referralSource("PHONE")
                .createdAt(Instant.now())
                .build();

        when(admissionEnquiryService.captureWalkInEnquiry(any(CaptureEnquiryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidateFirstName").value("Priya"));
    }

    // --- Validation Failure Cases (400 Bad Request) ---

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Missing required fields returns 400 Bad Request with VALIDATION_FAILED")
    void testCaptureEnquiry_ValidationFailure() throws Exception {
        // Empty request — missing all required fields
        CaptureEnquiryRequest request = new CaptureEnquiryRequest();

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + counselorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Invalid email format returns 400 Bad Request")
    void testCaptureEnquiry_InvalidEmail() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "not-an-email", "+919876543210",
                "WALK_IN", null, null, null
        );

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + counselorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Invalid phone format returns 400 Bad Request")
    void testCaptureEnquiry_InvalidPhone() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "sanjay@example.com", "abc-invalid",
                "WALK_IN", null, null, null
        );

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + counselorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- Unauthenticated (401 Unauthorized) ---

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - No token returns 401 Unauthorized")
    void testCaptureEnquiry_Unauthenticated() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "sanjay@example.com", "+919876543210",
                "WALK_IN", null, null, null
        );

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- Authorization Failure (403 Forbidden) ---

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Teacher role forbidden returns 403")
    void testCaptureEnquiry_Forbidden() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "sanjay@example.com", "+919876543210",
                "WALK_IN", null, null, null
        );

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- Conflict (409) ---

    @Test
    @DisplayName("POST /api/v1/admissions/enquiries - Duplicate guardian phone returns 409 Conflict")
    void testCaptureEnquiry_DuplicatePhone_Conflict() throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(
                "tenant-001", "branch-001",
                "Rohan", "Mehta",
                "grade-10-id", "ay-2026-id",
                "Sanjay Mehta", "sanjay@example.com", "+919876543210",
                "WALK_IN", null, null, null
        );

        when(admissionEnquiryService.captureWalkInEnquiry(any(CaptureEnquiryRequest.class)))
                .thenThrow(new ConflictException("An enquiry already exists with guardian phone +919876543210"));

        mockMvc.perform(post("/api/v1/admissions/enquiries")
                        .header("Authorization", "Bearer " + counselorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- Not Found (404) ---

    @Test
    @DisplayName("GET /api/v1/admissions/enquiries/{enquiryId} - Non-existent enquiry returns 404 Not Found")
    void testGetEnquiry_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(admissionEnquiryService.getEnquiryById(nonExistentId))
                .thenThrow(new ResourceNotFoundException("AdmissionEnquiry", "enquiryId", nonExistentId));

        mockMvc.perform(get("/api/v1/admissions/enquiries/" + nonExistentId)
                        .header("Authorization", "Bearer " + counselorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- GET Success ---

    @Test
    @DisplayName("GET /api/v1/admissions/enquiries/{enquiryId} - Returns enquiry details")
    void testGetEnquiry_Success() throws Exception {
        AdmissionEnquiryResponse response = AdmissionEnquiryResponse.builder()
                .id(1L)
                .enquiryId(ENQUIRY_UUID)
                .tenantId("tenant-001")
                .branchId("branch-001")
                .applicationNumber("APP-2026-ABCD1234")
                .candidateFirstName("Rohan")
                .candidateLastName("Mehta")
                .enquiryStatus("NEW_INQUIRY")
                .build();

        when(admissionEnquiryService.getEnquiryById(ENQUIRY_UUID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admissions/enquiries/" + ENQUIRY_UUID)
                        .header("Authorization", "Bearer " + counselorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationNumber").value("APP-2026-ABCD1234"))
                .andExpect(jsonPath("$.data.candidateFirstName").value("Rohan"));
    }

    // --- Trace Header Propagation ---

    @Test
    @DisplayName("Header Propagation - X-Trace-Id and X-Span-Id are preserved in response")
    void testTraceHeaderPropagation() throws Exception {
        AdmissionEnquiryResponse response = AdmissionEnquiryResponse.builder()
                .id(1L)
                .enquiryId(ENQUIRY_UUID)
                .tenantId("tenant-001")
                .build();

        when(admissionEnquiryService.getEnquiryById(ENQUIRY_UUID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admissions/enquiries/" + ENQUIRY_UUID)
                        .header("Authorization", "Bearer " + counselorToken)
                        .header("X-Trace-Id", "custom-trace-999")
                        .header("X-Span-Id", "custom-span-888"))
                .andExpect(header().string("X-Trace-Id", "custom-trace-999"))
                .andExpect(header().string("X-Span-Id", "custom-span-888"));
    }
}
