package com.kvs.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.kvs.admission.dto.*;
import com.kvs.admission.entity.AuditLog;
import com.kvs.admission.enums.*;
import com.kvs.admission.repo.AuditLogRepository;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test of US-02011 .. US-02020 (and the parent / counselor grants of the scenario repeats)
 * through the HTTP API with real services and the database. Uses its own tenant per run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdmissionOperationsEndToEndTest {

    private static final Path VAULT_DIR;

    static {
        try {
            VAULT_DIR = Files.createTempDirectory("admission-ops-vault-test");
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
    private final String tenantId = "tenant-ops-" + run;
    private final String branchId = "branch-ops";
    private final String gradeId = "GRADE-06";
    private final String yearId = "AY-2026-27";

    private String officer;
    private String counselor;
    private String registrar;
    private String evaluator;
    private String cashier;
    private String principal;
    private String vicePrincipal;
    private String marketing;

    @BeforeEach
    void tokens() {
        officer = token("officer.ops@kvs.com", "ADMISSION_OFFICER");
        counselor = token("counselor.ops@kvs.com", "FRONT_OFFICE_COUNSELOR");
        registrar = token("registrar.ops@kvs.com", "REGISTRAR");
        evaluator = token("evaluator.ops@kvs.com", "ENTRANCE_EXAM_EVALUATOR");
        cashier = token("cashier.ops@kvs.com", "FINANCE_CASHIER");
        principal = token("principal.ops@kvs.com", "SCHOOL_PRINCIPAL");
        vicePrincipal = token("vice.principal.ops@kvs.com", "SCHOOL_PRINCIPAL");
        marketing = token("marketing.ops@kvs.com", "MARKETING_MANAGER");
    }

    @Test
    @DisplayName("Admission operations: campaigns, calls, follow-ups, TC check, PDF, waitlist, notices, waivers, sections, CPA, archive")
    void admissionOperations() throws Exception {
        // --- US-02019 campaign, US-02001 leads with attribution -------------------------------------
        String campaignCode = "OPENHOUSE-" + run.toUpperCase();
        MvcResult campaign = perform(post("/api/v1/admissions/marketing-campaigns"), marketing, new CreateCampaignRequest(tenantId,
                campaignCode, "Open House 2026", "SOCIAL_MEDIA", LocalDate.now().minusDays(10), LocalDate.now().plusDays(30),
                new BigDecimal("100000")))
                .andExpect(status().isCreated()).andReturn();
        String campaignId = read(campaign, "$.data.campaignId");

        UUID asha = capture(counselor, "Asha", "Rao", "asha." + run + "@example.com", phone(1), campaignCode);
        UUID rohan = capture(counselor, "Rohan", "Mehta", "rohan." + run + "@example.com", phone(2), campaignCode);
        UUID kiran = capture(counselor, "Kiran", "Das", "kiran." + run + "@example.com", phone(3), null);
        perform(post("/api/v1/admissions/enquiries"), counselor, enquiry("Ghost", "Lead", "ghost." + run + "@example.com", phone(9), "NO-SUCH-CAMPAIGN"))
                .andExpect(status().isBadRequest());

        // --- US-02021 / US-02061: parent self-service enquiry -----------------------------------------
        String devEmail = "dev.parent." + run + "@example.com";
        String parent = token(devEmail, "APPLICANT_PARENT");
        perform(post("/api/v1/admissions/enquiries"), parent, enquiry("Dev", "Iyer", "someone.else." + run + "@example.com", phone(4), null))
                .andExpect(status().isForbidden());
        MvcResult devResult = perform(post("/api/v1/admissions/enquiries"), parent, enquiry("Dev", "Iyer", devEmail, phone(5), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referralSource").value("PARENT_PORTAL"))
                .andReturn();
        UUID dev = UUID.fromString(read(devResult, "$.data.enquiryId"));
        mockMvc.perform(get("/api/v1/admissions/enquiries/" + dev).header("Authorization", parent)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admissions/enquiries/" + asha).header("Authorization", parent)).andExpect(status().isForbidden());

        // --- US-02015: call log and automatic callbacks ----------------------------------------------
        String calls = "/api/v1/admissions/enquiries/" + dev + "/calls";
        perform(post(calls), counselor, call(CallOutcome.NO_ANSWER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledCallback.status").value("SCHEDULED"));
        perform(post(calls), counselor, call(CallOutcome.CONNECTED))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.enquiryStatus").value("CONTACTED"))
                .andExpect(jsonPath("$.data.scheduledCallback").doesNotExist());
        mockMvc.perform(get("/api/v1/admissions/callbacks").param("tenantId", tenantId).header("Authorization", counselor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0)); // the first callback was completed by the connected call
        perform(post(calls), counselor, call(CallOutcome.NO_ANSWER)).andExpect(jsonPath("$.data.scheduledCallback").exists());
        perform(post(calls), counselor, call(CallOutcome.BUSY)).andExpect(jsonPath("$.data.scheduledCallback").exists());
        perform(post(calls), counselor, call(CallOutcome.NO_ANSWER))
                .andExpect(jsonPath("$.data.scheduledCallback").doesNotExist())
                .andExpect(jsonPath("$.data.callbackNote").value(containsString("3 unanswered attempts")));
        perform(post(calls), counselor, call(CallOutcome.NOT_INTERESTED))
                .andExpect(jsonPath("$.data.enquiryStatus").value("WITHDRAWN"));
        mockMvc.perform(get(calls).header("Authorization", marketing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        // --- Exams, evaluations and merit list (US-02002, US-02003, US-02010) ---------------------------
        perform(post("/api/v1/admissions/entrance-exams"), officer, new ScheduleEntranceExamRequest(tenantId, branchId, "Entrance Test",
                yearId, gradeId, Instant.now().plus(3, ChronoUnit.HOURS), 90, "Hall A", new BigDecimal("100"), new BigDecimal("40"),
                null, List.of(asha, rohan, kiran)))
                .andExpect(status().isCreated());
        evaluate(asha, 38, 27, 25, new int[]{18, 27, 18, 12, 12});   // 90 / 87
        evaluate(rohan, 30, 25, 25, new int[]{15, 20, 15, 10, 10});  // 80 / 70
        evaluate(kiran, 30, 23, 22, new int[]{14, 18, 15, 9, 9});    // 75 / 65
        MvcResult merit = perform(post("/api/v1/admissions/merit-lists"), officer, new GenerateMeritListRequest(tenantId, branchId,
                yearId, gradeId, new BigDecimal("0.6"), new BigDecimal("0.4")))
                .andExpect(status().isCreated()).andReturn();
        String meritListId = read(merit, "$.data.meritListId");

        // --- US-02012: noticeboard PDF ------------------------------------------------------------------
        MvcResult pdf = mockMvc.perform(get("/api/v1/admissions/merit-lists/" + meritListId + "/pdf").header("Authorization", principal))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        // --- US-02011 follow-ups and US-02017 board check -------------------------------------------------
        String ashaDocs = "/api/v1/admissions/enquiries/" + asha + "/documents";
        String birthCertificate = upload(evaluator, ashaDocs, DocumentType.BIRTH_CERTIFICATE);
        ScanDocumentFollowUpsRequest scan = new ScanDocumentFollowUpsRequest(tenantId, branchId, 3);
        perform(post("/api/v1/admissions/document-follow-ups/scan"), registrar, scan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicantsScanned").value(3))
                .andExpect(jsonPath("$.data.newlyFlagged").value(3));

        String tcVerify = "/api/v1/admissions/enquiries/" + asha + "/transfer-certificate/verifications";
        perform(post(tcVerify), counselor, new VerifyTransferCertificateRequest(EducationBoard.CBSE, "CBSE/2730125/2026/004512", null))
                .andExpect(status().isBadRequest()); // TC not uploaded yet
        upload(evaluator, ashaDocs, DocumentType.TRANSFER_CERTIFICATE);
        perform(post(tcVerify), counselor, new VerifyTransferCertificateRequest(EducationBoard.CBSE, "TC-2026-17", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("INVALID"))
                .andExpect(jsonPath("$.data.documentVerificationStatus").value("REJECTED"));
        upload(evaluator, ashaDocs, DocumentType.TRANSFER_CERTIFICATE); // a rejected document may be replaced
        perform(post(tcVerify), counselor, new VerifyTransferCertificateRequest(EducationBoard.CBSE, "CBSE/2730125/2026/004512", "St. Mary's"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("VALID"))
                .andExpect(jsonPath("$.data.documentVerificationStatus").value("VERIFIED"));
        perform(post(tcVerify), counselor, new VerifyTransferCertificateRequest(EducationBoard.CBSE, "CBSE/2730125/2026/004512", null))
                .andExpect(status().isConflict());
        perform(patch("/api/v1/admissions/documents/" + birthCertificate + "/verification"), registrar,
                new VerifyDocumentRequest(DocumentVerificationStatus.VERIFIED, "Original sighted"))
                .andExpect(status().isOk());
        perform(post("/api/v1/admissions/document-follow-ups/scan"), registrar, scan)
                .andExpect(jsonPath("$.data.autoResolved").value(1))
                .andExpect(jsonPath("$.data.updated").value(2))
                .andExpect(jsonPath("$.data.openFollowUps.length()").value(2));

        // --- US-02014 waitlist and seat escalation --------------------------------------------------------
        perform(put("/api/v1/admissions/waitlists/rules"), officer, new WaitlistRuleRequest(tenantId, branchId, yearId, gradeId, 2, 7,
                new BigDecimal("25000.00"), false))
                .andExpect(status().isOk());
        MvcResult ashaOffer = perform(post("/api/v1/admissions/offers"), cashier,
                new IssueOfferRequest(asha, null, LocalDate.now().plusDays(10), new BigDecimal("25000.00")))
                .andExpect(status().isCreated()).andReturn();
        String ashaOfferId = read(ashaOffer, "$.data.offerId");
        perform(post("/api/v1/admissions/waitlists/entries"), officer, new AddToWaitlistRequest(kiran)).andExpect(status().isCreated());
        perform(post("/api/v1/admissions/waitlists/entries"), officer, new AddToWaitlistRequest(rohan)).andExpect(status().isCreated());
        perform(post("/api/v1/admissions/waitlists/entries"), officer, new AddToWaitlistRequest(rohan)).andExpect(status().isConflict());
        perform(post("/api/v1/admissions/waitlists/entries"), officer, new AddToWaitlistRequest(asha)).andExpect(status().isBadRequest());
        String scope = "?tenantId=" + tenantId + "&branchId=" + branchId + "&academicYearId=" + yearId + "&gradeId=" + gradeId;
        mockMvc.perform(get("/api/v1/admissions/waitlists" + scope).header("Authorization", officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupiedSeats").value(1))
                .andExpect(jsonPath("$.data.freeSeats").value(1))
                .andExpect(jsonPath("$.data.entries[0].candidateName").value("Rohan Mehta")) // higher merit score queues first
                .andExpect(jsonPath("$.data.entries[0].position").value(1));
        MvcResult escalation = perform(post("/api/v1/admissions/waitlists/escalation"), officer,
                new WaitlistScopeRequest(tenantId, branchId, yearId, gradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeSeatsBefore").value(1))
                .andExpect(jsonPath("$.data.offersIssued").value(1))
                .andExpect(jsonPath("$.data.stillWaiting").value(1))
                .andReturn();
        String rohanOfferId = read(escalation, "$.data.issuedOffers[0].offerId");
        perform(post("/api/v1/admissions/waitlists/escalation"), officer, new WaitlistScopeRequest(tenantId, branchId, yearId, gradeId))
                .andExpect(jsonPath("$.data.offersIssued").value(0)); // grade is full

        // --- US-02018 bulk acceptance notices ------------------------------------------------------------
        BulkAcceptanceRequest notices = new BulkAcceptanceRequest(tenantId, branchId, yearId, gradeId, null);
        perform(post("/api/v1/admissions/acceptance-notifications"), officer, notices)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poolSize").value(2))
                .andExpect(jsonPath("$.data.sent").value(2));
        perform(post("/api/v1/admissions/acceptance-notifications"), officer, notices)
                .andExpect(jsonPath("$.data.sent").value(0))
                .andExpect(jsonPath("$.data.skipped").value(2));

        // --- US-02016 scholarship waivers -------------------------------------------------------------------
        perform(post("/api/v1/admissions/offers/" + rohanOfferId + "/fee-waivers"), officer,
                new RequestFeeWaiverRequest(ScholarshipCategory.MERIT, 50, "High merit"))
                .andExpect(status().isBadRequest()); // Rohan scored 80 < 85
        MvcResult meritWaiver = perform(post("/api/v1/admissions/offers/" + ashaOfferId + "/fee-waivers"), officer,
                new RequestFeeWaiverRequest(ScholarshipCategory.MERIT, 50, "Entrance test score 90, rank 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payableAmount").value(12500.00))
                .andReturn();
        perform(patch("/api/v1/admissions/fee-waivers/" + read(meritWaiver, "$.data.waiverId") + "/decision"), principal,
                new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, "Approved by principal"))
                .andExpect(status().isOk());
        String deposit = "/api/v1/admissions/offers/" + ashaOfferId + "/deposits";
        perform(post(deposit), principal, new CollectDepositRequest(new BigDecimal("25000.00"), PaymentMode.UPI, "UPI-OPS-" + run, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("12500.00")));
        perform(post(deposit), principal, new CollectDepositRequest(BigDecimal.ZERO.add(new BigDecimal("0.01")), PaymentMode.WAIVER, null, null))
                .andExpect(status().isBadRequest());
        perform(post(deposit), principal, new CollectDepositRequest(new BigDecimal("12500.00"), PaymentMode.UPI, "UPI-OPS-" + run, null))
                .andExpect(status().isCreated());

        MvcResult ewsWaiver = perform(post("/api/v1/admissions/offers/" + rohanOfferId + "/fee-waivers"), principal,
                new RequestFeeWaiverRequest(ScholarshipCategory.ECONOMICALLY_WEAKER_SECTION, 100, "EWS certificate EWS/DL/2026/88123"))
                .andExpect(status().isCreated()).andReturn();
        String ewsDecision = "/api/v1/admissions/fee-waivers/" + read(ewsWaiver, "$.data.waiverId") + "/decision";
        perform(patch(ewsDecision), principal, new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, null))
                .andExpect(status().isForbidden()); // four-eyes
        perform(patch(ewsDecision), vicePrincipal, new FeeWaiverDecisionRequest(WaiverStatus.APPROVED, "Certificate verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.waiverReceiptNumber").value(containsString("RCPT-")));

        // --- US-02005 conversion, US-02013 sections -----------------------------------------------------------
        MvcResult student = perform(post("/api/v1/admissions/enquiries/" + asha + "/student-conversion"), counselor,
                new ConvertLeadRequest(LocalDate.of(2015, 6, 1), Gender.FEMALE))
                .andExpect(status().isCreated()).andReturn(); // counselor granted (US-02025)
        String studentId = read(student, "$.data.studentId");
        perform(put("/api/v1/admissions/grade-sections"), officer, new ConfigureGradeSectionsRequest(tenantId, branchId, yearId, gradeId,
                List.of(new ConfigureGradeSectionsRequest.SectionInput("A", 1, "Ms. Rekha"),
                        new ConfigureGradeSectionsRequest.SectionInput("B", 1, "Mr. Alok"))))
                .andExpect(status().isOk());
        perform(post("/api/v1/admissions/grade-sections/auto-placement"), registrar, new AutoPlacementRequest(tenantId, branchId, yearId, gradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placed").value(1))
                .andExpect(jsonPath("$.data.placements[0].sectionCode").value("A"));
        perform(put("/api/v1/admissions/students/" + studentId + "/placement"), officer, new PlaceStudentRequest(gradeId, "B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionCode").value("B"));
        perform(put("/api/v1/admissions/grade-sections"), officer, new ConfigureGradeSectionsRequest(tenantId, branchId, yearId, gradeId,
                List.of(new ConfigureGradeSectionsRequest.SectionInput("B", 1, null))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admissions/students/" + studentId).header("Authorization", token("asha." + run + "@example.com", "APPLICANT_PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionCode").value("B"));

        // --- US-02019 cost per acquisition -------------------------------------------------------------------
        perform(patch("/api/v1/admissions/marketing-campaigns/" + campaignId), marketing, new UpdateCampaignRequest(new BigDecimal("50000"), null))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admissions/analytics/cost-per-acquisition").param("tenantId", tenantId).header("Authorization", marketing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.campaignMetrics[0].leads").value(2))
                .andExpect(jsonPath("$.data.campaignMetrics[0].enrolledLeads").value(1))
                .andExpect(jsonPath("$.data.campaignMetrics[0].costPerLead").value(25000.00))
                .andExpect(jsonPath("$.data.campaignMetrics[0].costPerAcquisition").value(50000.00))
                .andExpect(jsonPath("$.data.mostEfficientCampaign").value(campaignCode));

        // --- US-02020 archive and trends ----------------------------------------------------------------------
        perform(post("/api/v1/admissions/lead-archives"), principal, new ArchiveLeadsRequest(tenantId, null, true, 30))
                .andExpect(jsonPath("$.data.archived").value(0))
                .andExpect(jsonPath("$.data.tooRecent").value(1));
        perform(post("/api/v1/admissions/lead-archives"), principal, new ArchiveLeadsRequest(tenantId, null, true, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(1))
                .andExpect(jsonPath("$.data.withdrawn").value(1));
        mockMvc.perform(get("/api/v1/admissions/enquiries").param("tenantId", tenantId).param("branchId", branchId)
                        .header("Authorization", counselor))
                .andExpect(jsonPath("$.data.length()").value(3)); // archived lead left the active list
        mockMvc.perform(get("/api/v1/admissions/analytics/archived-lead-trends").param("tenantId", tenantId).header("Authorization", marketing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalArchived").value(1))
                .andExpect(jsonPath("$.data.byReferralSource.PARENT_PORTAL").value(1));
        perform(post(calls), counselor, call(CallOutcome.CONNECTED)).andExpect(status().isBadRequest()); // archived

        // --- Audit trail ----------------------------------------------------------------------------------------
        Set<String> actions = auditLogRepository.findAll().stream()
                .filter(a -> a.getResource() != null && a.getResource().endsWith(":" + tenantId))
                .map(AuditLog::getAction).collect(Collectors.toSet());
        assertThat(actions).contains("CREATE_MARKETING_CAMPAIGN", "LOG_COUNSELING_CALL", "EXPORT_MERIT_LIST_PDF", "SCAN_DOCUMENT_FOLLOW_UPS",
                "VERIFY_TRANSFER_CERTIFICATE", "SAVE_WAITLIST_RULE", "ADD_TO_WAITLIST", "ESCALATE_WAITLIST",
                "SEND_BULK_ACCEPTANCE_NOTIFICATIONS", "REQUEST_FEE_WAIVER", "DECIDE_FEE_WAIVER", "CONFIGURE_GRADE_SECTIONS",
                "AUTO_PLACE_STUDENTS", "PLACE_STUDENT_IN_SECTION", "UPDATE_MARKETING_CAMPAIGN", "VIEW_COST_PER_ACQUISITION_REPORT",
                "ARCHIVE_CLOSED_LEADS");
    }

    // ------------------------------------------------------------------------------------------------------------

    private CaptureEnquiryRequest enquiry(String first, String last, String email, String phone, String campaignCode) {
        return new CaptureEnquiryRequest(tenantId, branchId, first, last, gradeId, yearId, first + " Parent", email, phone,
                campaignCode != null ? "SOCIAL_MEDIA" : null, null, null, campaignCode);
    }

    private UUID capture(String token, String first, String last, String email, String phone, String campaignCode) throws Exception {
        MvcResult result = perform(post("/api/v1/admissions/enquiries"), token, enquiry(first, last, email, phone, campaignCode))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(read(result, "$.data.enquiryId"));
    }

    private LogCallRequest call(CallOutcome outcome) {
        return new LogCallRequest(CallDirection.OUTBOUND, outcome, outcome == CallOutcome.CONNECTED ? 240 : 0,
                "Call outcome " + outcome, null, null);
    }

    private void evaluate(UUID enquiry, int maths, int language, int reasoning, int[] interview) throws Exception {
        Map<RubricCriterion, Integer> entrance = Map.of(RubricCriterion.MATHEMATICS, maths, RubricCriterion.LANGUAGE, language,
                RubricCriterion.LOGICAL_REASONING, reasoning);
        List<RubricCriterion> interviewCriteria = RubricCriterion.forType(EvaluationType.INTERVIEW);
        Map<RubricCriterion, Integer> talk = new EnumMap<>(RubricCriterion.class);
        for (int i = 0; i < interviewCriteria.size(); i++) {
            talk.put(interviewCriteria.get(i), interview[i]);
        }
        for (Map.Entry<EvaluationType, Map<RubricCriterion, Integer>> e : Map.of(EvaluationType.ENTRANCE_TEST, entrance,
                EvaluationType.INTERVIEW, talk).entrySet()) {
            perform(post("/api/v1/admissions/evaluations"), registrar, new RecordEvaluationRequest(enquiry, e.getKey(), null, null,
                    new BigDecimal("50"), e.getValue().entrySet().stream()
                    .map(s -> new RubricScoreInput(s.getKey(), BigDecimal.valueOf(s.getValue()), null)).toList(), null))
                    .andExpect(status().isCreated());
        }
    }

    private String upload(String token, String url, DocumentType type) throws Exception {
        MvcResult result = mockMvc.perform(multipart(url)
                        .file(new MockMultipartFile("file", type.name().toLowerCase() + ".pdf", "application/pdf",
                                ("%PDF-1.7\n" + type + " " + UUID.randomUUID() + "\n%%EOF").getBytes()))
                        .param("documentType", type.name())
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.data.documentId");
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        return mockMvc.perform(builder.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String token(String email, String role) {
        return "Bearer " + jwtTokenService.generateToken(email, Map.of("roles", List.of("ROLE_" + role)));
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String phone(int candidate) {
        String value = String.valueOf(Math.abs((run + "ops" + candidate).hashCode()));
        return "+9191" + (value + "0000000000").substring(0, 7) + candidate;
    }
}
