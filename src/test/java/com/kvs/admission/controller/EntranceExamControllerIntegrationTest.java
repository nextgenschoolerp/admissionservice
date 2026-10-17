package com.kvs.admission.controller;

import com.kvs.admission.dto.AdmitCardResponse;
import com.kvs.admission.dto.EntranceExamResponse;
import com.kvs.admission.dto.ScheduleEntranceExamRequest;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link EntranceExamController}.
 * Covers: US-02002: Schedule entrance examination dates and auto-generate candidate admit cards
 * Actor: Admission Officer
 */
class EntranceExamControllerIntegrationTest extends AdmissionControllerTestSupport {

    private static final String URL = "/api/v1/admissions/entrance-exams";
    private static final UUID EXAM_ID = UUID.fromString("e2000000-0000-0000-0000-000000000001");
    private static final UUID ENQUIRY_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    private ScheduleEntranceExamRequest validRequest() {
        return new ScheduleEntranceExamRequest("tenant-001", "branch-001", "Grade 6 Entrance Test 2026",
                "ay-2026-id", "grade-6-id", Instant.now().plus(5, ChronoUnit.DAYS), 90, "Main Hall, Block A",
                new BigDecimal("100"), new BigDecimal("40"), "Report 30 minutes early with a pencil",
                List.of(ENQUIRY_ID));
    }

    private EntranceExamResponse examResponse() {
        AdmitCardResponse card = AdmitCardResponse.builder()
                .admitCardNumber("ADMIT-2026-AAAA1111")
                .rollNumber("EXAM-2026-1A2B3C4D-001")
                .seatNumber("S-001")
                .candidateName("Rohan Mehta")
                .enquiryId(ENQUIRY_ID)
                .status("ISSUED")
                .build();
        return EntranceExamResponse.builder()
                .examId(EXAM_ID)
                .examCode("EXAM-2026-1A2B3C4D")
                .examTitle("Grade 6 Entrance Test 2026")
                .status("SCHEDULED")
                .admitCardCount(1)
                .admitCards(List.of(card))
                .build();
    }

    @Test
    @DisplayName("POST entrance-exams - Admission Officer schedules exam and gets admit cards (201)")
    void scheduleExam_Success() throws Exception {
        when(entranceExamService.scheduleExam(any(ScheduleEntranceExamRequest.class))).thenReturn(examResponse());

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .header("X-Trace-Id", "test-trace-02002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "test-trace-02002"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Entrance exam scheduled and admit cards generated successfully"))
                .andExpect(jsonPath("$.data.examCode").value("EXAM-2026-1A2B3C4D"))
                .andExpect(jsonPath("$.data.admitCardCount").value(1))
                .andExpect(jsonPath("$.data.admitCards[0].rollNumber").value("EXAM-2026-1A2B3C4D-001"));
    }

    @Test
    @DisplayName("POST entrance-exams - Missing fields returns 400")
    void scheduleExam_ValidationFailure() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ScheduleEntranceExamRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST entrance-exams - Exam date in the past returns 400")
    void scheduleExam_PastDate() throws Exception {
        ScheduleEntranceExamRequest request = validRequest();
        request.setExamStartAt(Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST entrance-exams - No token returns 401")
    void scheduleExam_Unauthenticated() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST entrance-exams - Front-Office Counselor is forbidden (403)")
    void scheduleExam_Forbidden() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST entrance-exams - Candidate already holds an upcoming admit card returns 409")
    void scheduleExam_Conflict() throws Exception {
        when(entranceExamService.scheduleExam(any(ScheduleEntranceExamRequest.class)))
                .thenThrow(new ConflictException("Candidates already hold an admit card for an upcoming entrance exam"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("ADMISSION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET entrance-exams/{examId} - Unknown exam returns 404")
    void getExam_NotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(entranceExamService.getExam(unknown))
                .thenThrow(new ResourceNotFoundException("EntranceExamSchedule", "examId", unknown));

        mockMvc.perform(get(URL + "/" + unknown).header("Authorization", bearer("ADMISSION_OFFICER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET entrance-exams/{examId}/admit-cards - Counselor lists admit cards with trace headers")
    void listAdmitCards_Success() throws Exception {
        when(entranceExamService.listAdmitCards(EXAM_ID)).thenReturn(examResponse().getAdmitCards());

        mockMvc.perform(get(URL + "/" + EXAM_ID + "/admit-cards")
                        .header("Authorization", bearer("FRONT_OFFICE_COUNSELOR"))
                        .header("X-Trace-Id", "trace-02002")
                        .header("X-Span-Id", "span-02002"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-02002"))
                .andExpect(header().string("X-Span-Id", "span-02002"))
                .andExpect(jsonPath("$.data[0].seatNumber").value("S-001"));
    }
}
