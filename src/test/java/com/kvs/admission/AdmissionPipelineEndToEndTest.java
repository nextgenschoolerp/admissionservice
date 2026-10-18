package com.kvs.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.kvs.admission.dto.*;
import com.kvs.admission.entity.AuditLog;
import com.kvs.admission.enums.*;
import com.kvs.admission.repo.AuditLogRepository;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of US-02001..US-02010 through the HTTP API with real services and the database.
 * Each run uses its own tenant so data from earlier runs never interferes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdmissionPipelineEndToEndTest {

    private static final Path VAULT_DIR;

    static {
        try {
            VAULT_DIR = Files.createTempDirectory("admission-vault-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void vaultProperties(DynamicPropertyRegistry registry) {
        registry.add("admission.document-vault.base-path", VAULT_DIR::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String tenantId = "tenant-e2e-" + run;
    private final String branchId = "branch-e2e";
    private final String gradeId = "grade-6";
    private final String yearId = "ay-2026";

    @Test
    @DisplayName("Admission pipeline: enquiry -> exam -> reminders -> evaluation -> merit list -> documents -> offer -> deposit -> student")
    void fullAdmissionPipeline() throws Exception {
        String officer = token("officer@kvs.com", "ADMISSION_OFFICER");
        String counselor = token("counselor@kvs.com", "FRONT_OFFICE_COUNSELOR");
        String registrar = token("registrar@kvs.com", "REGISTRAR");
        String evaluator = token("evaluator@kvs.com", "ENTRANCE_EXAM_EVALUATOR");
        String cashier = token("cashier@kvs.com", "FINANCE_CASHIER");
        String principal = token("principal@kvs.com", "SCHOOL_PRINCIPAL");
        String marketing = token("marketing@kvs.com", "MARKETING_MANAGER");

        // --- US-02001: three walk-in leads ---------------------------------------------------------
        String ashaParentEmail = "asha.parent." + run + "@example.com";
        UUID asha = captureEnquiry(counselor, "Asha", "Rao", ashaParentEmail, "+9190000" + digits(5, 1));
        UUID rohan = captureEnquiry(counselor, "Rohan", "Mehta", "rohan.parent." + run + "@example.com", "+9190000" + digits(5, 2));
        UUID kiran = captureEnquiry(counselor, "Kiran", "Das", "kiran.parent." + run + "@example.com", "+9190000" + digits(5, 3));

        // --- US-02002: schedule exam and auto-generate admit cards ----------------------------------
        ScheduleEntranceExamRequest examRequest = new ScheduleEntranceExamRequest(tenantId, branchId,
                "Grade 6 Entrance Test", yearId, gradeId, Instant.now().plus(3, ChronoUnit.HOURS), 90,
                "Main Hall", new BigDecimal("100"), new BigDecimal("40"), "Report 30 minutes early",
                List.of(asha, rohan, kiran));
        MvcResult examResult = perform(post("/api/v1/admissions/entrance-exams"), officer, examRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.admitCardCount").value(3))
                .andExpect(jsonPath("$.data.admitCards[0].seatNumber").value("S-001"))
                .andReturn();
        String examCode = read(examResult, "$.data.examCode");
        assertThat((String) read(examResult, "$.data.admitCards[2].rollNumber")).isEqualTo(examCode + "-003");

        // A candidate cannot hold two admit cards for upcoming exams
        perform(post("/api/v1/admissions/entrance-exams"), officer, withCandidates(examRequest, List.of(asha)))
                .andExpect(status().isConflict());

        // --- US-02009: WhatsApp reminders are idempotent ---------------------------------------------
        DispatchRemindersRequest reminders = new DispatchRemindersRequest(tenantId, branchId, 24);
        perform(post("/api/v1/admissions/reminders/entrance-evaluations"), counselor, reminders)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examsCovered").value(1))
                .andExpect(jsonPath("$.data.remindersSent").value(3))
                .andExpect(jsonPath("$.data.notifications[0].status").value("SENT"));
        perform(post("/api/v1/admissions/reminders/entrance-evaluations"), counselor, reminders)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindersSent").value(0))
                .andExpect(jsonPath("$.data.remindersSkipped").value(3));

        // --- US-02003: rubric evaluations ----------------------------------------------------------
        perform(post("/api/v1/admissions/evaluations"), registrar,
                evaluation(asha, EvaluationType.ENTRANCE_TEST, Map.of(RubricCriterion.MATHEMATICS, 38)))
                .andExpect(status().isBadRequest()); // incomplete rubric
        perform(post("/api/v1/admissions/evaluations"), registrar,
                evaluation(asha, EvaluationType.INTERVIEW, scores(RubricCriterion.COMMUNICATION, 25,
                        RubricCriterion.ACADEMIC_APTITUDE, 27, RubricCriterion.GENERAL_AWARENESS, 18,
                        RubricCriterion.CONFIDENCE_AND_CONDUCT, 12, RubricCriterion.PARENT_INTERACTION, 12)))
                .andExpect(status().isBadRequest()); // 25 > max 20

        perform(post("/api/v1/admissions/evaluations"), registrar,
                evaluation(asha, EvaluationType.ENTRANCE_TEST, scores(RubricCriterion.MATHEMATICS, 38,
                        RubricCriterion.LANGUAGE, 27, RubricCriterion.LOGICAL_REASONING, 25)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalScore").value(90))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.enquiryStatus").value("EVALUATION_PASSED"));
        perform(post("/api/v1/admissions/evaluations"), registrar,
                evaluation(asha, EvaluationType.ENTRANCE_TEST, scores(RubricCriterion.MATHEMATICS, 1,
                        RubricCriterion.LANGUAGE, 1, RubricCriterion.LOGICAL_REASONING, 1)))
                .andExpect(status().isConflict()); // already evaluated
        perform(post("/api/v1/admissions/evaluations"), registrar,
                evaluation(asha, EvaluationType.INTERVIEW, scores(RubricCriterion.COMMUNICATION, 18,
                        RubricCriterion.ACADEMIC_APTITUDE, 27, RubricCriterion.GENERAL_AWARENESS, 18,
                        RubricCriterion.CONFIDENCE_AND_CONDUCT, 12, RubricCriterion.PARENT_INTERACTION, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalScore").value(87));
        for (UUID candidate : List.of(rohan, kiran)) {
            perform(post("/api/v1/admissions/evaluations"), registrar,
                    evaluation(candidate, EvaluationType.ENTRANCE_TEST, scores(RubricCriterion.MATHEMATICS, 30,
                            RubricCriterion.LANGUAGE, 25, RubricCriterion.LOGICAL_REASONING, 25)))
                    .andExpect(status().isCreated());
            perform(post("/api/v1/admissions/evaluations"), registrar,
                    evaluation(candidate, EvaluationType.INTERVIEW, scores(RubricCriterion.COMMUNICATION, 15,
                            RubricCriterion.ACADEMIC_APTITUDE, 20, RubricCriterion.GENERAL_AWARENESS, 15,
                            RubricCriterion.CONFIDENCE_AND_CONDUCT, 10, RubricCriterion.PARENT_INTERACTION, 10)))
                    .andExpect(status().isCreated());
        }

        // --- US-02010: weighted merit ranking (ties share a rank) ------------------------------------
        perform(post("/api/v1/admissions/merit-lists"), officer, new GenerateMeritListRequest(tenantId, branchId,
                yearId, gradeId, new BigDecimal("0.6"), new BigDecimal("0.3")))
                .andExpect(status().isBadRequest()); // weights must add up to 1
        MvcResult merit = perform(post("/api/v1/admissions/merit-lists"), officer, new GenerateMeritListRequest(
                tenantId, branchId, yearId, gradeId, new BigDecimal("0.6"), new BigDecimal("0.4")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rankedCandidates").value(3))
                .andExpect(jsonPath("$.data.entries[0].candidateName").value("Asha Rao"))
                .andExpect(jsonPath("$.data.entries[0].weightedScore").value(88.8))
                .andExpect(jsonPath("$.data.entries[0].meritRank").value(1))
                .andExpect(jsonPath("$.data.entries[1].meritRank").value(2))
                .andExpect(jsonPath("$.data.entries[2].meritRank").value(2))
                .andExpect(jsonPath("$.data.entries[2].weightedScore").value(76.0))
                .andReturn();
        String meritListId = read(merit, "$.data.meritListId");
        mockMvc.perform(get("/api/v1/admissions/merit-lists/" + meritListId).header("Authorization", registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(3));

        // --- US-02004: document vault ---------------------------------------------------------------
        String docsUrl = "/api/v1/admissions/enquiries/" + asha + "/documents";
        mockMvc.perform(multipart(docsUrl)
                        .file(new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ\u0090binary".getBytes()))
                        .param("documentType", "BIRTH_CERTIFICATE")
                        .header("Authorization", evaluator))
                .andExpect(status().isBadRequest()); // content does not match declared type
        String birthCertId = uploadPdf(evaluator, docsUrl, DocumentType.BIRTH_CERTIFICATE);
        String transferCertId = uploadPdf(evaluator, docsUrl, DocumentType.TRANSFER_CERTIFICATE);

        String ashaParent = token(ashaParentEmail, "APPLICANT_PARENT");
        ConvertLeadRequest conversion = new ConvertLeadRequest(LocalDate.of(2014, 6, 1), Gender.FEMALE);
        String conversionUrl = "/api/v1/admissions/enquiries/" + asha + "/student-conversion";
        perform(post(conversionUrl), ashaParent, conversion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not yet verified")));

        perform(patch("/api/v1/admissions/documents/" + birthCertId + "/verification"), registrar,
                new VerifyDocumentRequest(DocumentVerificationStatus.REJECTED, null))
                .andExpect(status().isBadRequest()); // rejection needs notes
        for (String documentId : List.of(birthCertId, transferCertId)) {
            perform(patch("/api/v1/admissions/documents/" + documentId + "/verification"), registrar,
                    new VerifyDocumentRequest(DocumentVerificationStatus.VERIFIED, "Original sighted"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
        }
        try (var files = Files.walk(VAULT_DIR)) {
            assertThat(files.filter(Files::isRegularFile).count()).isGreaterThanOrEqualTo(2);
        }

        // --- US-02006: offer letter with payment link -----------------------------------------------
        IssueOfferRequest offerRequest = new IssueOfferRequest(asha, null, LocalDate.now().plusDays(10),
                new BigDecimal("15000.00"));
        perform(post("/api/v1/admissions/offers"), cashier,
                new IssueOfferRequest(rohan, null, LocalDate.now().plusDays(10), new BigDecimal("15000.00")))
                .andExpect(status().isCreated()); // Rohan also passed
        MvcResult offer = perform(post("/api/v1/admissions/offers"), cashier, offerRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offerStatus").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.letterContent").value(org.hamcrest.Matchers.containsString("Asha Rao")))
                .andReturn();
        String offerId = read(offer, "$.data.offerId");
        String paymentLink = read(offer, "$.data.paymentLinkUrl");
        assertThat(paymentLink).startsWith("https://pay.kvs.local/admissions/offers?offer=OFFER-").contains("&token=");
        perform(post("/api/v1/admissions/offers"), cashier, offerRequest).andExpect(status().isConflict());

        perform(post(conversionUrl), ashaParent, conversion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has not been paid")));

        // --- US-02008: seat reservation deposit and digital receipt ----------------------------------
        String depositUrl = "/api/v1/admissions/offers/" + offerId + "/deposits";
        perform(post(depositUrl), principal, new CollectDepositRequest(new BigDecimal("1000.00"), PaymentMode.UPI, "UPI-" + run, null))
                .andExpect(status().isBadRequest()); // partial payment
        perform(post(depositUrl), principal, new CollectDepositRequest(new BigDecimal("15000.00"), PaymentMode.UPI, null, null))
                .andExpect(status().isBadRequest()); // UPI needs a reference
        MvcResult receipt = perform(post(depositUrl), principal,
                new CollectDepositRequest(new BigDecimal("15000.00"), PaymentMode.UPI, "UPI-" + run, "Paid at counter"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.candidateName").value("Asha Rao"))
                .andReturn();
        String receiptNumber = read(receipt, "$.data.receiptNumber");
        perform(post(depositUrl), principal,
                new CollectDepositRequest(new BigDecimal("15000.00"), PaymentMode.CASH, null, null))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/admissions/deposits/receipts/" + receiptNumber).header("Authorization", principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionReference").value("UPI-" + run));

        // --- US-02005: conversion into Student Master record -----------------------------------------
        String otherParent = token("someone.else." + run + "@example.com", "APPLICANT_PARENT");
        perform(post(conversionUrl), otherParent, conversion).andExpect(status().isForbidden());
        MvcResult student = perform(post(conversionUrl), ashaParent, conversion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.gradeId").value(gradeId))
                .andReturn();
        assertThat((String) read(student, "$.data.admissionNumber")).startsWith("ADM-");
        perform(post(conversionUrl), ashaParent, conversion).andExpect(status().isConflict());
        String studentId = read(student, "$.data.studentId");
        mockMvc.perform(get("/api/v1/admissions/students/" + studentId).header("Authorization", ashaParent))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admissions/students/" + studentId).header("Authorization", otherParent))
                .andExpect(status().isForbidden());

        // --- US-02007: conversion analytics -----------------------------------------------------------
        mockMvc.perform(get("/api/v1/admissions/analytics/conversion-rates")
                        .param("tenantId", tenantId)
                        .header("Authorization", marketing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLeads").value(3))
                .andExpect(jsonPath("$.data.enrolledLeads").value(1))
                .andExpect(jsonPath("$.data.overallConversionRate").value(33.33))
                .andExpect(jsonPath("$.data.topChannel").value("WALK_IN"))
                .andExpect(jsonPath("$.data.channels[0].offersIssued").value(2))
                .andExpect(jsonPath("$.data.channels[0].qualifiedLeads").value(3));

        // --- Audit trail: every story logged an entry for this tenant ---------------------------------
        Set<String> actions = auditLogRepository.findAll().stream()
                .filter(a -> a.getResource() != null && a.getResource().endsWith(":" + tenantId))
                .map(AuditLog::getAction)
                .collect(Collectors.toSet());
        assertThat(actions).contains("SCHEDULE_ENTRANCE_EXAM", "RECORD_APPLICANT_EVALUATION",
                "UPLOAD_APPLICANT_DOCUMENT", "VERIFY_APPLICANT_DOCUMENT", "CONVERT_LEAD_TO_STUDENT",
                "ISSUE_ADMISSION_OFFER", "VIEW_LEAD_CONVERSION_REPORT", "COLLECT_SEAT_DEPOSIT",
                "DISPATCH_WHATSAPP_EXAM_REMINDERS", "GENERATE_MERIT_LIST");
    }

    // ------------------------------------------------------------------------------------------------

    private UUID captureEnquiry(String token, String first, String last, String email, String phone) throws Exception {
        CaptureEnquiryRequest request = new CaptureEnquiryRequest(tenantId, branchId, first, last, gradeId, yearId,
                first + " Parent", email, phone, "WALK_IN", null, null, null);
        MvcResult result = perform(post("/api/v1/admissions/enquiries"), token, request)
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.data.enquiryId"));
    }

    private String uploadPdf(String token, String url, DocumentType type) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", type.name().toLowerCase() + ".pdf",
                "application/pdf", ("%PDF-1.7\n" + type + " for test\n%%EOF").getBytes());
        MvcResult result = mockMvc.perform(multipart(url).file(file)
                        .param("documentType", type.name())
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.checksumSha256").isString())
                .andReturn();
        return read(result, "$.data.documentId");
    }

    private RecordEvaluationRequest evaluation(UUID enquiryId, EvaluationType type, Map<RubricCriterion, Integer> scores) {
        List<RubricScoreInput> inputs = scores.entrySet().stream()
                .map(e -> new RubricScoreInput(e.getKey(), BigDecimal.valueOf(e.getValue()), null))
                .toList();
        return new RecordEvaluationRequest(enquiryId, type, null, null, new BigDecimal("50"), inputs, null);
    }

    private static Map<RubricCriterion, Integer> scores(Object... pairs) {
        Map<RubricCriterion, Integer> map = new EnumMap<>(RubricCriterion.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((RubricCriterion) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private ScheduleEntranceExamRequest withCandidates(ScheduleEntranceExamRequest base, List<UUID> candidates) {
        return new ScheduleEntranceExamRequest(base.getTenantId(), base.getBranchId(), base.getExamTitle(),
                base.getAcademicYearId(), base.getTargetGradeId(), base.getExamStartAt().plus(1, ChronoUnit.DAYS),
                base.getDurationMinutes(), base.getVenue(), base.getMaxMarks(), base.getPassingMarks(),
                base.getReportingInstructions(), candidates);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        return mockMvc.perform(builder
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String token(String email, String role) {
        return "Bearer " + jwtTokenService.generateToken(email, Map.of("roles", List.of("ROLE_" + role)));
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    /** Builds a phone suffix that is unique per run so repeated runs never collide. */
    private String digits(int length, int candidate) {
        String value = String.valueOf(Math.abs((run + candidate).hashCode()));
        return (value + "0000000000").substring(0, length) + candidate;
    }
}
