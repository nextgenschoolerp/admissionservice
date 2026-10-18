package com.kvs.admission.controller;

import com.kvs.admission.service.MeritListService;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the merit list PDF export in {@link MeritListController}.
 * Covers: US-02012 — Export merit list rankings to PDF for public noticeboard display
 */
class MeritListPdfExportIntegrationTest extends AdmissionControllerTestSupport {

    private static final UUID LIST_ID = UUID.fromString("5eed0002-000a-4000-8000-000000000001");

    @Test
    @DisplayName("GET pdf - Principal downloads the noticeboard PDF with masked names by default")
    void exportPdf_Success() throws Exception {
        when(meritListService.exportPdf(LIST_ID, false))
                .thenReturn(new MeritListService.PdfExport("MERIT-2026-4D8E21F0.pdf", "%PDF-1.7 test".getBytes()));

        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + LIST_ID + "/pdf").header("Authorization", bearer("SCHOOL_PRINCIPAL")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"MERIT-2026-4D8E21F0.pdf\""))
                .andExpect(content().bytes("%PDF-1.7 test".getBytes()));
        verify(meritListService).exportPdf(eq(LIST_ID), eq(false));
    }

    @Test
    @DisplayName("GET pdf?fullNames=true - Evaluator can request full names")
    void exportPdf_FullNames() throws Exception {
        when(meritListService.exportPdf(LIST_ID, true)).thenReturn(new MeritListService.PdfExport("M.pdf", "%PDF".getBytes()));

        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + LIST_ID + "/pdf").param("fullNames", "true")
                        .header("Authorization", bearer("ENTRANCE_EXAM_EVALUATOR")))
                .andExpect(status().isOk());
        verify(meritListService).exportPdf(eq(LIST_ID), eq(true));
    }

    @Test
    @DisplayName("GET pdf - Unknown merit list returns a JSON 404, not a broken PDF")
    void exportPdf_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(meritListService.exportPdf(unknown, false)).thenThrow(new ResourceNotFoundException("MeritList", "meritListId", unknown));

        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + unknown + "/pdf").header("Authorization", bearer("SCHOOL_PRINCIPAL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET pdf - No token returns 401; Applicant Parent returns 403")
    void exportPdf_Security() throws Exception {
        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + LIST_ID + "/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + LIST_ID + "/pdf").header("Authorization", bearer("APPLICANT_PARENT")))
                .andExpect(status().isForbidden());
    }
}
