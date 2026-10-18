package com.kvs.admission.service;

import com.kvs.admission.dto.ArchivedLeadTrendResponse;
import com.kvs.admission.dto.MeritListEntryResponse;
import com.kvs.admission.dto.MeritListResponse;
import com.kvs.admission.entity.ApplicantDocument;
import com.kvs.admission.entity.ArchivedLeadSnapshot;
import com.kvs.admission.entity.CounselingCall;
import com.kvs.admission.enums.*;
import com.kvs.admission.notification.EducationBoardGateway;
import com.kvs.admission.notification.StubEducationBoardGateway;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure business rules behind US-02011 .. US-02020.
 */
class AdmissionRulesUnitTest {

    private static Instant ist(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, CounselingCallService.SCHOOL_ZONE).toInstant();
    }

    @Nested
    @DisplayName("US-02011 document completeness")
    class DocumentCompleteness {

        private ApplicantDocument doc(DocumentType type, DocumentVerificationStatus status, String notes) {
            ApplicantDocument d = new ApplicantDocument();
            d.setDocumentType(type);
            d.setVerificationStatus(status);
            d.setVerificationNotes(notes);
            return d;
        }

        @Test
        @DisplayName("Missing, pending and rejected mandatory documents are each reported; optional ones are ignored")
        void reportsEachIssue() {
            Map<DocumentType, String> issues = DocumentFollowUpService.assessMandatoryDocuments(List.of(
                    doc(DocumentType.TRANSFER_CERTIFICATE, DocumentVerificationStatus.REJECTED, "not signed"),
                    doc(DocumentType.AADHAAR, DocumentVerificationStatus.PENDING, null)));
            assertThat(issues).containsOnlyKeys(DocumentType.BIRTH_CERTIFICATE, DocumentType.TRANSFER_CERTIFICATE);
            assertThat(issues.get(DocumentType.BIRTH_CERTIFICATE)).isEqualTo("not uploaded");
            assertThat(issues.get(DocumentType.TRANSFER_CERTIFICATE)).isEqualTo("rejected - not signed");

            assertThat(DocumentFollowUpService.assessMandatoryDocuments(List.of(
                    doc(DocumentType.BIRTH_CERTIFICATE, DocumentVerificationStatus.PENDING, null),
                    doc(DocumentType.TRANSFER_CERTIFICATE, DocumentVerificationStatus.VERIFIED, null))))
                    .containsExactly(Map.entry(DocumentType.BIRTH_CERTIFICATE, "uploaded, awaiting verification"));
        }

        @Test
        @DisplayName("All mandatory documents verified means no issues")
        void complete() {
            assertThat(DocumentFollowUpService.assessMandatoryDocuments(List.of(
                    doc(DocumentType.BIRTH_CERTIFICATE, DocumentVerificationStatus.VERIFIED, null),
                    doc(DocumentType.TRANSFER_CERTIFICATE, DocumentVerificationStatus.VERIFIED, null)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("US-02012 merit list PDF")
    class MeritPdf {

        private MeritListResponse list(int candidates) {
            List<MeritListEntryResponse> entries = IntStream.rangeClosed(1, candidates).mapToObj(i -> MeritListEntryResponse.builder()
                    .meritRank(i).applicationNumber("APP-2026-" + (1000 + i)).candidateName("Aarav Sharma" + (i == 1 ? "" : " " + i))
                    .weightedScore(new BigDecimal("80.50")).qualified(i % 2 == 1).build()).toList();
            return MeritListResponse.builder().meritListCode("MERIT-2026-4D8E21F0").branchId("DPS-DELHI-NORTH")
                    .academicYearId("AY-2026-27").targetGradeId("GRADE-06")
                    .entranceTestWeight(new BigDecimal("0.700")).interviewWeight(new BigDecimal("0.300")).entries(entries).build();
        }

        @Test
        @DisplayName("Names are masked unless full names are requested")
        void masksNames() throws Exception {
            assertThat(MeritListPdfRenderer.maskName("Aarav Sharma")).isEqualTo("Aarav S.");
            assertThat(MeritListPdfRenderer.maskName("Ananya Lakshmi Iyer")).isEqualTo("Ananya I.");
            assertThat(MeritListPdfRenderer.maskName("Madonna")).isEqualTo("Madonna");

            String masked = text(MeritListPdfRenderer.render(list(1), false));
            assertThat(masked).contains("Aarav S.").doesNotContain("Aarav Sharma").contains("Weights: entrance test 70%, interview 30%");
            assertThat(text(MeritListPdfRenderer.render(list(1), true))).contains("Aarav Sharma");
        }

        @Test
        @DisplayName("Long lists paginate and non-WinAnsi characters do not break rendering")
        void paginates() throws Exception {
            MeritListResponse big = list(MeritListPdfRenderer.ROWS_PER_PAGE + 5);
            big.getEntries().get(0).setCandidateName("आरव Sharma");
            byte[] pdf = MeritListPdfRenderer.render(big, true);
            assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                assertThat(doc.getNumberOfPages()).isEqualTo(2);
            }
            assertThat(text(pdf)).contains("Page 2 of 2").contains("Under review");
        }

        private String text(byte[] pdf) throws Exception {
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                return new PDFTextStripper().getText(doc);
            }
        }
    }

    @Nested
    @DisplayName("US-02013 section balancing")
    class SectionBalancing {

        @Test
        @DisplayName("Each student goes to the section with the most free seats; ties follow section order")
        void balances() {
            Map<String, Integer> free = new LinkedHashMap<>();
            free.put("A", 2);
            free.put("B", 3);
            free.put("C", 1);
            assertThat(SectionPlacementService.assignBalanced(free, 5)).containsExactly("B", "A", "B", "A", "B");
        }

        @Test
        @DisplayName("Students beyond total capacity get no section")
        void overflow() {
            assertThat(SectionPlacementService.assignBalanced(new LinkedHashMap<>(Map.of("A", 1)), 3)).containsExactly("A", null, null);
        }
    }

    @Nested
    @DisplayName("US-02014 waitlist seats")
    class WaitlistSeats {
        @Test
        void freeSeatsNeverNegative() {
            assertThat(WaitlistService.freeSeats(40, 37)).isEqualTo(3);
            assertThat(WaitlistService.freeSeats(40, 42)).isZero();
        }
    }

    @Nested
    @DisplayName("US-02015 callback scheduling")
    class Callbacks {

        @Test
        @DisplayName("No answer retries in 2 hours, busy in 30 minutes, within calling hours")
        void retryDelays() {
            Instant noon = ist(2026, 10, 19, 12, 0);
            assertThat(CounselingCallService.computeCallback(CallOutcome.NO_ANSWER, noon, null, 1)).contains(ist(2026, 10, 19, 14, 0));
            assertThat(CounselingCallService.computeCallback(CallOutcome.BUSY, noon, null, 1)).contains(ist(2026, 10, 19, 12, 30));
        }

        @Test
        @DisplayName("Automatic times after 19:00 or before 09:00 IST move to 10:00")
        void callingHours() {
            assertThat(CounselingCallService.computeCallback(CallOutcome.NO_ANSWER, ist(2026, 10, 19, 17, 30), null, 1))
                    .contains(ist(2026, 10, 20, 10, 0));
            assertThat(CounselingCallService.withinCallingHours(ist(2026, 10, 20, 7, 15))).isEqualTo(ist(2026, 10, 20, 10, 0));
        }

        @Test
        @DisplayName("Requested times are kept; unanswered calls stop after 3 attempts; connected calls need no callback")
        void otherRules() {
            Instant requested = ist(2026, 10, 21, 20, 30);
            Instant now = ist(2026, 10, 19, 12, 0);
            assertThat(CounselingCallService.computeCallback(CallOutcome.CALLBACK_REQUESTED, now, requested, 0)).contains(requested);
            assertThat(CounselingCallService.computeCallback(CallOutcome.NO_ANSWER, now, null, 3)).isEmpty();
            assertThat(CounselingCallService.computeCallback(CallOutcome.CONNECTED, now, null, 0)).isEmpty();
        }

        @Test
        @DisplayName("Unanswered streak counts consecutive NO_ANSWER/BUSY calls from the newest")
        void streak() {
            List<CounselingCall> newestFirst = Stream.of(CallOutcome.BUSY, CallOutcome.NO_ANSWER, CallOutcome.CONNECTED, CallOutcome.NO_ANSWER)
                    .map(o -> { CounselingCall c = new CounselingCall(); c.setOutcome(o); return c; }).toList();
            assertThat(CounselingCallService.unansweredStreak(newestFirst)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("US-02016 waiver rules")
    class Waivers {
        @Test
        void capsAndMeritThreshold() {
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.SIBLING, 25, null)).isNull();
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.SIBLING, 30, null)).contains("capped at 25%");
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.ECONOMICALLY_WEAKER_SECTION, 100, null)).isNull();
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.MERIT, 50, new BigDecimal("88.00"))).isNull();
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.MERIT, 50, new BigDecimal("84.50"))).contains("scored 84.5");
            assertThat(FeeWaiverService.validateWaiver(ScholarshipCategory.MERIT, 50, null)).contains("at least 85");
            assertThat(FeeWaiverService.waivedAmount(new BigDecimal("25000.00"), 35)).isEqualByComparingTo("8750.00");
        }
    }

    @Nested
    @DisplayName("US-02017 board registry stub")
    class BoardStub {
        private final StubEducationBoardGateway gateway = new StubEducationBoardGateway();

        @Test
        void deterministicResults() {
            EducationBoardGateway.BoardLookupResult valid = gateway.verifyTransferCertificate(EducationBoard.CBSE,
                    "CBSE/2730125/2026/004512", "Aarav Sharma", "St. Mary's School");
            assertThat(valid.result()).isEqualTo(TcVerificationResult.VALID);
            assertThat(valid.boardReference()).isEqualTo("CBSE-REG-2730125-004512");
            assertThat(gateway.verifyTransferCertificate(EducationBoard.CBSE, "TC-12345", null, null).result())
                    .isEqualTo(TcVerificationResult.INVALID);
            assertThat(gateway.verifyTransferCertificate(EducationBoard.ICSE, "CBSE/2730125/2026/004512", null, null).message())
                    .contains("issued under CBSE");
            assertThat(gateway.verifyTransferCertificate(EducationBoard.ICSE, "ICSE/880123/2025/120000", null, null).result())
                    .isEqualTo(TcVerificationResult.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("US-02019 cost per acquisition")
    class Cpa {
        @Test
        void divide() {
            assertThat(MarketingCampaignService.divide(new BigDecimal("85000"), 2)).isEqualByComparingTo("42500.00");
            assertThat(MarketingCampaignService.divide(new BigDecimal("1000"), 3)).isEqualByComparingTo("333.33");
            assertThat(MarketingCampaignService.divide(new BigDecimal("85000"), 0)).isNull();
        }
    }

    @Nested
    @DisplayName("US-02020 archive trends")
    class ArchiveTrends {
        private ArchivedLeadSnapshot snapshot(String status, Instant closedAt, String score) {
            ArchivedLeadSnapshot s = new ArchivedLeadSnapshot();
            s.setFinalStatus(status);
            s.setClosedAt(closedAt);
            s.setEntranceTestScore(score != null ? new BigDecimal(score) : null);
            return s;
        }

        @Test
        @DisplayName("Buckets by IST closing month with rejected entrance score average")
        void monthlyBuckets() {
            List<ArchivedLeadTrendResponse.MonthBucket> buckets = LeadArchiveService.monthlyBuckets(List.of(
                    snapshot("REJECTED", ist(2026, 9, 23, 10, 0), "31.00"),
                    snapshot("REJECTED", ist(2026, 9, 25, 10, 0), "42.00"),
                    snapshot("WITHDRAWN", ist(2026, 9, 2, 10, 0), null),
                    // 23:30 UTC on 31 Oct is already 1 Nov in IST
                    snapshot("REJECTED", Instant.parse("2026-10-31T23:30:00Z"), null)));
            assertThat(buckets).extracting(ArchivedLeadTrendResponse.MonthBucket::getMonth).containsExactly("2026-09", "2026-11");
            assertThat(buckets.get(0).getRejected()).isEqualTo(2);
            assertThat(buckets.get(0).getWithdrawn()).isEqualTo(1);
            assertThat(buckets.get(0).getAverageRejectedEntranceScore()).isEqualByComparingTo("36.50");
            assertThat(buckets.get(1).getAverageRejectedEntranceScore()).isNull();
        }
    }

}
