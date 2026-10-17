package com.kvs.admission.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.admission.service.*;
import com.kvs.core.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

/**
 * Shared setup for the US-02002..US-02010 controller tests. All services are mocked in one place
 * so every subclass reuses the same cached Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AdmissionControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockBean
    protected EntranceExamService entranceExamService;

    @MockBean
    protected ApplicantEvaluationService evaluationService;

    @MockBean
    protected ApplicantDocumentService documentService;

    @MockBean
    protected StudentConversionService conversionService;

    @MockBean
    protected AdmissionOfferService offerService;

    @MockBean
    protected LeadConversionAnalyticsService analyticsService;

    @MockBean
    protected AdmissionFeeDepositService depositService;

    @MockBean
    protected ApplicantReminderService reminderService;

    @MockBean
    protected MeritListService meritListService;

    protected String bearer(String role) {
        String user = role.toLowerCase().replace('_', '.') + "@kvs.com";
        return "Bearer " + jwtTokenService.generateToken(user, Map.of("roles", List.of("ROLE_" + role)));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
