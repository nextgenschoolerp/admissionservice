package com.kvs.admission.controller;

import com.kvs.admission.dto.*;
import com.kvs.core.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link SectionPlacementController}.
 * Covers: US-02013 — Assign target grade levels and sections to confirmed admission leads
 */
class SectionPlacementControllerIntegrationTest extends AdmissionControllerTestSupport {

    private ConfigureGradeSectionsRequest sections(String code, int capacity) {
        return new ConfigureGradeSectionsRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06",
                List.of(new ConfigureGradeSectionsRequest.SectionInput(code, capacity, "Ms. Rekha Joshi")));
    }

    @Test
    @DisplayName("PUT grade-sections - Admission Officer configures sections (200)")
    void configure_Success() throws Exception {
        when(placementService.configureSections(any())).thenReturn(List.of(GradeSectionResponse.builder()
                .sectionCode("A").capacity(40).placedStudents(0).availableSeats(40).build()));

        mockMvc.perform(put("/api/v1/admissions/grade-sections").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(sections("A", 40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].availableSeats").value(40));
    }

    @Test
    @DisplayName("PUT grade-sections - Lower-case section code and capacity 0 return 400")
    void configure_ValidationFailure() throws Exception {
        mockMvc.perform(put("/api/v1/admissions/grade-sections").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(sections("a", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    @DisplayName("POST auto-placement - Registrar places unplaced students (200); Counselor 403")
    void autoPlace() throws Exception {
        when(placementService.autoPlace(any())).thenReturn(AutoPlacementResponse.builder().placed(3).unplaced(0).build());
        String body = json(new AutoPlacementRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06"));

        mockMvc.perform(post("/api/v1/admissions/grade-sections/auto-placement").header("Authorization", bearer("REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placed").value(3));
        mockMvc.perform(post("/api/v1/admissions/grade-sections/auto-placement").header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT students/{id}/placement - Full section returns 409; no token 401")
    void place_Errors() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(placementService.placeStudent(eq(studentId), any())).thenThrow(new ConflictException("Section GRADE-06-A is full (40 seats)"));
        String body = json(new PlaceStudentRequest("GRADE-06", "A"));

        mockMvc.perform(put("/api/v1/admissions/students/" + studentId + "/placement").header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admissions/students/" + studentId + "/placement")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
