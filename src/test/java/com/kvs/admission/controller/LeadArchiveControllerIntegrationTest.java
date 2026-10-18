package com.kvs.admission.controller;

import com.kvs.admission.dto.ArchiveLeadsRequest;
import com.kvs.admission.dto.ArchiveLeadsResponse;
import com.kvs.admission.dto.ArchivedLeadTrendResponse;
import com.kvs.core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link LeadArchiveController}.
 * Covers: US-02020 — Archive rejected inquiry leads for historical trend reporting (School Principal)
 */
class LeadArchiveControllerIntegrationTest extends AdmissionControllerTestSupport {

    @Test
    @DisplayName("POST lead-archives - Principal archives closed leads (200)")
    void archive_Success() throws Exception {
        when(archiveService.archive(any())).thenReturn(ArchiveLeadsResponse.builder().archiveBatchId(UUID.randomUUID())
                .archived(2).rejected(1).withdrawn(1).applicationNumbers(List.of("APP-2026-3F9A1C05", "APP-2026-3F9A1C10")).build());

        mockMvc.perform(post("/api/v1/admissions/lead-archives").header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new ArchiveLeadsRequest("tenant-001", null, true, 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(2))
                .andExpect(jsonPath("$.data.applicationNumbers[0]").value("APP-2026-3F9A1C05"));
    }

    @Test
    @DisplayName("POST lead-archives - Negative age returns 400; Entrance Exam Evaluator 403")
    void archive_ValidationAndRole() throws Exception {
        mockMvc.perform(post("/api/v1/admissions/lead-archives").header("Authorization", bearer("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new ArchiveLeadsRequest("tenant-001", null, true, -1))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admissions/lead-archives").header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(new ArchiveLeadsRequest("tenant-001", null, true, 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET archived-lead-trends - Marketing Manager reads monthly trends; inverted range 400")
    void trends() throws Exception {
        when(archiveService.trends(eq("tenant-001"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(ArchivedLeadTrendResponse.builder().totalArchived(2)
                        .months(List.of(ArchivedLeadTrendResponse.MonthBucket.builder().month("2026-09").rejected(1).withdrawn(1).build()))
                        .build());
        when(archiveService.trends(eq("tenant-001"), eq(LocalDate.of(2026, 10, 31)), eq(LocalDate.of(2026, 1, 1))))
                .thenThrow(new BadRequestException("fromDate must not be after toDate"));

        mockMvc.perform(get("/api/v1/admissions/analytics/archived-lead-trends").param("tenantId", "tenant-001")
                        .param("fromDate", "2026-01-01").param("toDate", "2026-10-31").header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months[0].month").value("2026-09"));
        mockMvc.perform(get("/api/v1/admissions/analytics/archived-lead-trends").param("tenantId", "tenant-001")
                        .param("fromDate", "2026-10-31").param("toDate", "2026-01-01").header("Authorization", bearer("MARKETING_MANAGER")))
                .andExpect(status().isBadRequest());
    }
}
