package com.kvs.admission.controller;

import com.kvs.admission.dto.*;
import com.kvs.admission.enums.*;
import com.kvs.admission.service.MeritListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Verifies the actor decision for every story US-02011 .. US-02100.
 * The backlog assigns actors by rotation; each row states whether that actor was granted access to the
 * capability's endpoint (true) or deliberately not granted (false), as recorded in task.md.
 * Granted actors must get a 2xx response; actors not granted must get 403.
 */
class ScenarioActorAccessTest extends AdmissionControllerTestSupport {

    private static final UUID ID = UUID.fromString("5eed0002-0001-4000-8000-000000000001");

    @BeforeEach
    void stubPdf() {
        when(meritListService.exportPdf(any(UUID.class), anyBoolean()))
                .thenReturn(new MeritListService.PdfExport("MERIT.pdf", "%PDF-1.7".getBytes()));
    }

    static Stream<Arguments> stories() {
        return Stream.of(
                Arguments.of("US-02011", "DOCUMENT_FOLLOW_UP_SCAN", "REGISTRAR", true),
                Arguments.of("US-02012", "MERIT_LIST_PDF", "ENTRANCE_EXAM_EVALUATOR", true),
                Arguments.of("US-02013", "SECTION_AUTO_PLACEMENT", "APPLICANT_PARENT", false),
                Arguments.of("US-02014", "WAITLIST_ESCALATION", "FINANCE_CASHIER", false),
                Arguments.of("US-02015", "CALL_HISTORY", "MARKETING_MANAGER", true),
                Arguments.of("US-02016", "FEE_WAIVER_DECISION", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02017", "TC_VERIFICATION", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02018", "BULK_ACCEPTANCE", "ADMISSION_OFFICER", true),
                Arguments.of("US-02019", "COST_PER_ACQUISITION", "REGISTRAR", false),
                Arguments.of("US-02020", "LEAD_ARCHIVE", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02021", "CAPTURE_ENQUIRY", "APPLICANT_PARENT", true),
                Arguments.of("US-02022", "SCHEDULE_EXAM", "FINANCE_CASHIER", false),
                Arguments.of("US-02023", "RECORD_EVALUATION", "MARKETING_MANAGER", false),
                Arguments.of("US-02024", "UPLOAD_DOCUMENT", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02025", "CONVERT_LEAD", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02026", "ISSUE_OFFER", "ADMISSION_OFFICER", true),
                Arguments.of("US-02027", "CONVERSION_RATES", "REGISTRAR", true),
                Arguments.of("US-02028", "COLLECT_DEPOSIT", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02029", "DISPATCH_REMINDERS", "APPLICANT_PARENT", false),
                Arguments.of("US-02030", "GENERATE_MERIT_LIST", "FINANCE_CASHIER", false),
                Arguments.of("US-02031", "DOCUMENT_FOLLOW_UP_SCAN", "MARKETING_MANAGER", false),
                Arguments.of("US-02032", "MERIT_LIST_PDF", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02033", "SECTION_AUTO_PLACEMENT", "FRONT_OFFICE_COUNSELOR", false),
                Arguments.of("US-02034", "WAITLIST_ESCALATION", "ADMISSION_OFFICER", true),
                Arguments.of("US-02035", "CALL_HISTORY", "REGISTRAR", false),
                Arguments.of("US-02036", "FEE_WAIVER_DECISION", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02037", "TC_VERIFICATION", "APPLICANT_PARENT", false),
                Arguments.of("US-02038", "BULK_ACCEPTANCE", "FINANCE_CASHIER", false),
                Arguments.of("US-02039", "COST_PER_ACQUISITION", "MARKETING_MANAGER", true),
                Arguments.of("US-02040", "LEAD_ARCHIVE", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02041", "CAPTURE_ENQUIRY", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02042", "SCHEDULE_EXAM", "ADMISSION_OFFICER", true),
                Arguments.of("US-02043", "RECORD_EVALUATION", "REGISTRAR", true),
                Arguments.of("US-02044", "UPLOAD_DOCUMENT", "ENTRANCE_EXAM_EVALUATOR", true),
                Arguments.of("US-02045", "CONVERT_LEAD", "APPLICANT_PARENT", true),
                Arguments.of("US-02046", "ISSUE_OFFER", "FINANCE_CASHIER", true),
                Arguments.of("US-02047", "CONVERSION_RATES", "MARKETING_MANAGER", true),
                Arguments.of("US-02048", "COLLECT_DEPOSIT", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02049", "DISPATCH_REMINDERS", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02050", "GENERATE_MERIT_LIST", "ADMISSION_OFFICER", true),
                Arguments.of("US-02051", "DOCUMENT_FOLLOW_UP_SCAN", "REGISTRAR", true),
                Arguments.of("US-02052", "MERIT_LIST_PDF", "ENTRANCE_EXAM_EVALUATOR", true),
                Arguments.of("US-02053", "SECTION_AUTO_PLACEMENT", "APPLICANT_PARENT", false),
                Arguments.of("US-02054", "WAITLIST_ESCALATION", "FINANCE_CASHIER", false),
                Arguments.of("US-02055", "CALL_HISTORY", "MARKETING_MANAGER", true),
                Arguments.of("US-02056", "FEE_WAIVER_DECISION", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02057", "TC_VERIFICATION", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02058", "BULK_ACCEPTANCE", "ADMISSION_OFFICER", true),
                Arguments.of("US-02059", "COST_PER_ACQUISITION", "REGISTRAR", false),
                Arguments.of("US-02060", "LEAD_ARCHIVE", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02061", "CAPTURE_ENQUIRY", "APPLICANT_PARENT", true),
                Arguments.of("US-02062", "SCHEDULE_EXAM", "FINANCE_CASHIER", false),
                Arguments.of("US-02063", "RECORD_EVALUATION", "MARKETING_MANAGER", false),
                Arguments.of("US-02064", "UPLOAD_DOCUMENT", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02065", "CONVERT_LEAD", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02066", "ISSUE_OFFER", "ADMISSION_OFFICER", true),
                Arguments.of("US-02067", "CONVERSION_RATES", "REGISTRAR", true),
                Arguments.of("US-02068", "COLLECT_DEPOSIT", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02069", "DISPATCH_REMINDERS", "APPLICANT_PARENT", false),
                Arguments.of("US-02070", "GENERATE_MERIT_LIST", "FINANCE_CASHIER", false),
                Arguments.of("US-02071", "DOCUMENT_FOLLOW_UP_SCAN", "MARKETING_MANAGER", false),
                Arguments.of("US-02072", "MERIT_LIST_PDF", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02073", "SECTION_AUTO_PLACEMENT", "FRONT_OFFICE_COUNSELOR", false),
                Arguments.of("US-02074", "WAITLIST_ESCALATION", "ADMISSION_OFFICER", true),
                Arguments.of("US-02075", "CALL_HISTORY", "REGISTRAR", false),
                Arguments.of("US-02076", "FEE_WAIVER_DECISION", "ENTRANCE_EXAM_EVALUATOR", false),
                Arguments.of("US-02077", "TC_VERIFICATION", "APPLICANT_PARENT", false),
                Arguments.of("US-02078", "BULK_ACCEPTANCE", "FINANCE_CASHIER", false),
                Arguments.of("US-02079", "COST_PER_ACQUISITION", "MARKETING_MANAGER", true),
                Arguments.of("US-02080", "LEAD_ARCHIVE", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02081", "CAPTURE_ENQUIRY", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02082", "SCHEDULE_EXAM", "ADMISSION_OFFICER", true),
                Arguments.of("US-02083", "RECORD_EVALUATION", "REGISTRAR", true),
                Arguments.of("US-02084", "UPLOAD_DOCUMENT", "ENTRANCE_EXAM_EVALUATOR", true),
                Arguments.of("US-02085", "CONVERT_LEAD", "APPLICANT_PARENT", true),
                Arguments.of("US-02086", "ISSUE_OFFER", "FINANCE_CASHIER", true),
                Arguments.of("US-02087", "CONVERSION_RATES", "MARKETING_MANAGER", true),
                Arguments.of("US-02088", "COLLECT_DEPOSIT", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02089", "DISPATCH_REMINDERS", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02090", "GENERATE_MERIT_LIST", "ADMISSION_OFFICER", true),
                Arguments.of("US-02091", "DOCUMENT_FOLLOW_UP_SCAN", "REGISTRAR", true),
                Arguments.of("US-02092", "MERIT_LIST_PDF", "ENTRANCE_EXAM_EVALUATOR", true),
                Arguments.of("US-02093", "SECTION_AUTO_PLACEMENT", "APPLICANT_PARENT", false),
                Arguments.of("US-02094", "WAITLIST_ESCALATION", "FINANCE_CASHIER", false),
                Arguments.of("US-02095", "CALL_HISTORY", "MARKETING_MANAGER", true),
                Arguments.of("US-02096", "FEE_WAIVER_DECISION", "SCHOOL_PRINCIPAL", true),
                Arguments.of("US-02097", "TC_VERIFICATION", "FRONT_OFFICE_COUNSELOR", true),
                Arguments.of("US-02098", "BULK_ACCEPTANCE", "ADMISSION_OFFICER", true),
                Arguments.of("US-02099", "COST_PER_ACQUISITION", "REGISTRAR", false),
                Arguments.of("US-02100", "LEAD_ARCHIVE", "ENTRANCE_EXAM_EVALUATOR", false)
        );
    }

    @ParameterizedTest(name = "{0} {1} as {2} granted={3}")
    @MethodSource("stories")
    void actorAccessMatchesDecision(String storyId, String capability, String role, boolean granted) throws Exception {
        int status = mockMvc.perform(request(capability).header("Authorization", bearer(role)))
                .andReturn().getResponse().getStatus();
        if (granted) {
            assertThat(status).as(storyId + ": " + role + " should be allowed on " + capability).isBetween(200, 299);
        } else {
            assertThat(status).as(storyId + ": " + role + " should be denied on " + capability).isEqualTo(403);
        }
    }

    private MockHttpServletRequestBuilder request(String capability) throws Exception {
        String base = "/api/v1/admissions";
        return switch (capability) {
            case "DOCUMENT_FOLLOW_UP_SCAN" -> jsonPost(base + "/document-follow-ups/scan",
                    new ScanDocumentFollowUpsRequest("tenant-001", "branch-001", 3));
            case "MERIT_LIST_PDF" -> get(base + "/merit-lists/" + ID + "/pdf");
            case "SECTION_AUTO_PLACEMENT" -> jsonPost(base + "/grade-sections/auto-placement",
                    new AutoPlacementRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06"));
            case "WAITLIST_ESCALATION" -> jsonPost(base + "/waitlists/escalation",
                    new WaitlistScopeRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06"));
            case "CALL_HISTORY" -> get(base + "/enquiries/" + ID + "/calls");
            case "FEE_WAIVER_DECISION" -> patch(base + "/fee-waivers/" + ID + "/decision").contentType(MediaType.APPLICATION_JSON)
                    .content(json(new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, null)));
            case "TC_VERIFICATION" -> jsonPost(base + "/enquiries/" + ID + "/transfer-certificate/verifications",
                    new VerifyTransferCertificateRequest(EducationBoard.CBSE, "CBSE/2730125/2026/004512", "St. Mary's School"));
            case "BULK_ACCEPTANCE" -> jsonPost(base + "/acceptance-notifications",
                    new BulkAcceptanceRequest("tenant-001", "branch-001", "AY-2026-27", "GRADE-06", null));
            case "COST_PER_ACQUISITION" -> get(base + "/analytics/cost-per-acquisition").param("tenantId", "tenant-001");
            case "LEAD_ARCHIVE" -> jsonPost(base + "/lead-archives", new ArchiveLeadsRequest("tenant-001", null, true, 30));
            case "CAPTURE_ENQUIRY" -> jsonPost(base + "/enquiries", new CaptureEnquiryRequest("tenant-001", "branch-001", "Aarav",
                    "Sharma", "GRADE-06", "AY-2026-27", "Rohit Sharma", "rohit.sharma@gmail.com", "+919810012301", null, null, null, null));
            case "SCHEDULE_EXAM" -> jsonPost(base + "/entrance-exams", new ScheduleEntranceExamRequest("tenant-001", "branch-001",
                    "Round 3", "AY-2026-27", "GRADE-06", Instant.now().plus(3, ChronoUnit.DAYS), 90, "Hall A",
                    new BigDecimal("100"), new BigDecimal("40"), null, List.of(ID)));
            case "RECORD_EVALUATION" -> jsonPost(base + "/evaluations", new RecordEvaluationRequest(ID, EvaluationType.INTERVIEW, null,
                    null, new BigDecimal("50"), RubricCriterion.forType(EvaluationType.INTERVIEW).stream()
                    .map(c -> new RubricScoreInput(c, BigDecimal.TEN, null)).toList(), null));
            case "UPLOAD_DOCUMENT" -> multipart(base + "/enquiries/" + ID + "/documents")
                    .file(new MockMultipartFile("file", "tc.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                    .param("documentType", "TRANSFER_CERTIFICATE");
            case "CONVERT_LEAD" -> jsonPost(base + "/enquiries/" + ID + "/student-conversion",
                    new ConvertLeadRequest(LocalDate.of(2015, 3, 14), Gender.MALE));
            case "ISSUE_OFFER" -> jsonPost(base + "/offers", new IssueOfferRequest(ID, null, LocalDate.now().plusDays(10), new BigDecimal("25000")));
            case "CONVERSION_RATES" -> get(base + "/analytics/conversion-rates").param("tenantId", "tenant-001");
            case "COLLECT_DEPOSIT" -> jsonPost(base + "/offers/" + ID + "/deposits",
                    new CollectDepositRequest(new BigDecimal("25000"), PaymentMode.CASH, null, null));
            case "DISPATCH_REMINDERS" -> jsonPost(base + "/reminders/entrance-evaluations",
                    new DispatchRemindersRequest("tenant-001", "branch-001", 24));
            case "GENERATE_MERIT_LIST" -> jsonPost(base + "/merit-lists", new GenerateMeritListRequest("tenant-001", "branch-001",
                    "AY-2026-27", "GRADE-06", new BigDecimal("0.7"), new BigDecimal("0.3")));
            default -> throw new IllegalArgumentException(capability);
        };
    }

    private MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }
}
