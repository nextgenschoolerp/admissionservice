package com.kvs.admission.controller;

import com.kvs.admission.dto.GenerateMeritListRequest;
import com.kvs.admission.dto.MeritListEntryResponse;
import com.kvs.admission.dto.MeritListResponse;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link MeritListController}.
 * Covers: US-02010: Rank candidates automatically based on weighted entrance test scores
 * Actor: Admission Officer
 */
class MeritListControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/merit-lists";

    private final GenerateMeritListRequest validRequest = new GenerateMeritListRequest("tenant-001", "branch-001",
            "ay-2026-id", "grade-6-id", new BigDecimal("0.7"), new BigDecimal("0.3"));

    @Test
    @DisplayName("POST merit-lists - Admission Officer ranks candidates (201)")
    void generateMeritList_Success() throws Exception {
        MeritListResponse response = MeritListResponse.builder()
                .meritListId(UUID.randomUUID())
                .meritListCode("MERIT-2026-ABCD1234")
                .rankedCandidates(2)
                .entries(List.of(
                        MeritListEntryResponse.builder().meritRank(1).candidateName("Asha Rao")
                                .weightedScore(new BigDecimal("88.50")).build(),
                        MeritListEntryResponse.builder().meritRank(2).candidateName("Rohan Mehta")
                                .weightedScore(new BigDecimal("72.10")).build()))
                .build();
        when(meritListService.generateMeritList(any(GenerateMeritListRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .header("X-Trace-Id", "test-trace-02010")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02010"))
                .andExpect(jsonPath("$.data.rankedCandidates").value(2))
                .andExpect(jsonPath("$.data.entries[0].meritRank").value(1))
                .andExpect(jsonPath("$.data.entries[0].candidateName").value("Asha Rao"));
    }

    @Test
    @DisplayName("POST merit-lists - Weight above 1 returns 400")
    void generateMeritList_ValidationFailure() throws Exception {
        GenerateMeritListRequest request = new GenerateMeritListRequest("tenant-001", "branch-001",
                "ay-2026-id", "grade-6-id", new BigDecimal("1.5"), BigDecimal.ZERO);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST merit-lists - No evaluated candidates returns 400")
    void generateMeritList_NoCandidates() throws Exception {
        when(meritListService.generateMeritList(any(GenerateMeritListRequest.class)))
                .thenThrow(new BadRequestException("No candidates with complete evaluations found"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST merit-lists - No token returns 401")
    void generateMeritList_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST merit-lists - Registrar is forbidden (403)")
    void generateMeritList_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET merit-lists/{id} - Unknown merit list returns 404")
    void getMeritList_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(meritListService.getMeritList(unknown))
                .thenThrow(new ResourceNotFoundException("MeritList", "meritListId", unknown));

        mockMvc.perform(get(URL + "/" + unknown).header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isNotFound());
    }
}
