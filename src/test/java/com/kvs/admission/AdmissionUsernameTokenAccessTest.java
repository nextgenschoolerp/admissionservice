package com.kvs.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * authservice tokens carry the username as the subject and the email in an {@code email} claim.
 * Record-level rules that match by email (a parent's own enquiries, the capturing counselor) must use that claim.
 * Each run uses its own tenant so data from earlier runs never interferes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdmissionUsernameTokenAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String tenantId = "tenant-token-" + run;
    private final String guardianEmail = "guardian." + run + "@example.com";

    /** A token shaped like authservice's login token: username subject, role and email claims. */
    private String token(String username, String role, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of(role));
        if (email != null) {
            claims.put("email", email);
        }
        return "Bearer " + jwtTokenService.generateToken(username, claims);
    }

    private String capture(String bearer, String candidateFirstName) throws Exception {
        return capture(bearer, candidateFirstName, guardianEmail, "1");
    }

    /** One enquiry per guardian phone and email per tenant, so a second family needs its own contact details. */
    private String capture(String bearer, String candidateFirstName, String email, String phoneDigit) throws Exception {
        Map<String, Object> body = Map.of(
                "tenantId", tenantId, "branchId", "branch-token", "candidateFirstName", candidateFirstName, "candidateLastName", "Verma",
                "targetGradeId", "GRADE-06", "academicYearId", "AY-2026-27", "guardianName", "Sanjay Verma",
                "guardianEmail", email, "guardianPhone", "+9198" + phoneDigit + String.format("%07d", Math.floorMod(run.hashCode(), 10_000_000)));
        String json = mockMvc.perform(post("/api/v1/admissions/enquiries").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.enquiryId");
    }

    @Test
    @DisplayName("Counselor capture assigns the counselor's email; the parent (username token) can open their own enquiry")
    void counselorCaptureAndParentAccess() throws Exception {
        String enquiryId = capture(token("counselor_" + run, "FRONT_OFFICE_COUNSELOR", "counselor." + run + "@school.test"), "Aditi");

        mockMvc.perform(get("/api/v1/admissions/enquiries/" + enquiryId)
                        .header("Authorization", token("parent_" + run, "APPLICANT_PARENT", guardianEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedCounselorId").value("counselor." + run + "@school.test"));
    }

    @Test
    @DisplayName("A parent whose email differs from the guardian email is refused")
    void otherParentRefused() throws Exception {
        String enquiryId = capture(token("officer_" + run, "ADMISSION_OFFICER", "officer." + run + "@school.test"), "Ira");

        mockMvc.perform(get("/api/v1/admissions/enquiries/" + enquiryId)
                        .header("Authorization", token("parent2_" + run, "APPLICANT_PARENT", "someone.else@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Officer capture leaves the counselor unassigned; a parent's own online enquiry is allowed")
    void officerCaptureAndParentSelfService() throws Exception {
        String enquiryId = capture(token("officer_" + run, "ADMISSION_OFFICER", "officer." + run + "@school.test"), "Kiaan");
        String json = mockMvc.perform(get("/api/v1/admissions/enquiries/" + enquiryId)
                        .header("Authorization", token("officer_" + run, "ADMISSION_OFFICER", "officer." + run + "@school.test")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> data = JsonPath.read(json, "$.data");
        assertThat(data.get("assignedCounselorId")).isNull();

        String parentEmail = "parent.self." + run + "@example.com";
        capture(token("parent_" + run, "APPLICANT_PARENT", parentEmail), "Myra", parentEmail, "2");
    }
}
