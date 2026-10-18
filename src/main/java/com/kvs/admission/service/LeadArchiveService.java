package com.kvs.admission.service;

import com.kvs.admission.dto.ArchiveLeadsRequest;
import com.kvs.admission.dto.ArchiveLeadsResponse;
import com.kvs.admission.dto.ArchivedLeadTrendResponse;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.ArchivedLeadSnapshot;
import com.kvs.admission.entity.EntranceEvaluation;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.enums.EvaluationType;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.ArchivedLeadSnapshotRepository;
import com.kvs.admission.repo.EntranceEvaluationRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service archiving closed leads and reporting historical trends.
 * Covers user stories:
 * US-02020, US-02040, US-02060, US-02080, US-02100: Archive rejected inquiry leads for historical trend reporting
 * Actor: School Principal (US-02040/80). The Entrance Exam Evaluator (US-02020/60/100) is not granted.
 */
@Service
@Slf4j
public class LeadArchiveService {

    static final int DEFAULT_MINIMUM_DAYS_CLOSED = 30;
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private ArchivedLeadSnapshotRepository snapshotRepository;

    @Autowired
    private EntranceEvaluationRepository evaluationRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Archives REJECTED (and, unless disabled, WITHDRAWN) leads closed at least {@code minimumDaysClosed} days
     * ago: a snapshot is stored for trend reporting and the enquiry is marked archived, which removes it from
     * the active pipeline. Already-archived leads are not touched again.
     */
    @Transactional
    public ArchiveLeadsResponse archive(ArchiveLeadsRequest request) {
        boolean includeWithdrawn = request.getIncludeWithdrawn() == null || request.getIncludeWithdrawn();
        int minimumDays = request.getMinimumDaysClosed() != null ? request.getMinimumDaysClosed() : DEFAULT_MINIMUM_DAYS_CLOSED;
        Set<String> statuses = includeWithdrawn
                ? Set.of(EnquiryStatus.REJECTED.name(), EnquiryStatus.WITHDRAWN.name())
                : Set.of(EnquiryStatus.REJECTED.name());
        List<AdmissionEnquiry> closed = StringUtils.hasText(request.getBranchId())
                ? admissionEnquiryRepository.findByTenantIdAndBranchIdAndEnquiryStatusInAndArchivedAtIsNull(
                request.getTenantId(), request.getBranchId(), statuses)
                : admissionEnquiryRepository.findByTenantIdAndEnquiryStatusInAndArchivedAtIsNull(request.getTenantId(), statuses);

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(minimumDays));
        List<AdmissionEnquiry> eligible = closed.stream().filter(e -> !closedAt(e).isAfter(cutoff)).toList();

        Map<UUID, BigDecimal> entranceScores = eligible.isEmpty() ? Map.of()
                : evaluationRepository.findByEnquiryIdIn(eligible.stream().map(AdmissionEnquiry::getEnquiryId).toList()).stream()
                .filter(e -> e.getEvaluationType() == EvaluationType.ENTRANCE_TEST)
                .collect(Collectors.toMap(EntranceEvaluation::getEnquiryId, EntranceEvaluation::getTotalScore, (a, b) -> a));

        UUID batchId = UUID.randomUUID();
        String actor = SecurityUtils.getCurrentUserEmail().orElse("school_principal");
        int rejected = 0;
        for (AdmissionEnquiry e : eligible) {
            ArchivedLeadSnapshot s = new ArchivedLeadSnapshot();
            s.setArchiveBatchId(batchId);
            s.setTenantId(e.getTenantId());
            s.setBranchId(e.getBranchId());
            s.setEnquiryId(e.getEnquiryId());
            s.setApplicationNumber(e.getApplicationNumber());
            s.setFinalStatus(e.getEnquiryStatus());
            s.setReferralSource(e.getReferralSource());
            s.setCampaignCode(e.getCampaignCode());
            s.setTargetGradeId(e.getTargetGradeId());
            s.setAcademicYearId(e.getAcademicYearId());
            s.setEnquiryCreatedAt(e.getCreatedAt());
            s.setClosedAt(closedAt(e));
            s.setEntranceTestScore(entranceScores.get(e.getEnquiryId()));
            s.setClosureRemarks(e.getRemarks());
            s.setArchivedAt(now);
            s.setArchivedBy(actor);
            snapshotRepository.save(s);
            e.setArchivedAt(now);
            admissionEnquiryRepository.save(e);
            if (EnquiryStatus.REJECTED.name().equals(e.getEnquiryStatus())) {
                rejected++;
            }
        }

        auditService.logSuccess("lead_archive:" + request.getTenantId(), "ARCHIVE_CLOSED_LEADS",
                "Batch " + batchId + (StringUtils.hasText(request.getBranchId()) ? " | Branch: " + request.getBranchId() : "")
                        + " | Archived: " + eligible.size() + " (rejected " + rejected + ", withdrawn " + (eligible.size() - rejected) + ")"
                        + " | Closed less than " + minimumDays + " days ago: " + (closed.size() - eligible.size()));
        return ArchiveLeadsResponse.builder()
                .archiveBatchId(batchId)
                .archived(eligible.size())
                .rejected(rejected)
                .withdrawn(eligible.size() - rejected)
                .tooRecent(closed.size() - eligible.size())
                .cutoff(cutoff)
                .applicationNumbers(eligible.stream().map(AdmissionEnquiry::getApplicationNumber).sorted().toList())
                .build();
    }

    /**
     * Month-by-month archived lead counts (by closing month, IST) with channel and grade breakdowns.
     * Defaults to the last 12 months.
     */
    @Transactional(readOnly = true)
    public ArchivedLeadTrendResponse trends(String tenantId, LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate != null ? toDate : LocalDate.now(REPORT_ZONE);
        LocalDate from = fromDate != null ? fromDate : to.minusMonths(12).withDayOfMonth(1);
        if (from.isAfter(to)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }
        List<ArchivedLeadSnapshot> snapshots = snapshotRepository
                .findByTenantIdAndClosedAtGreaterThanEqualAndClosedAtLessThanOrderByClosedAtAsc(tenantId,
                        from.atStartOfDay(REPORT_ZONE).toInstant(), to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant());
        return ArchivedLeadTrendResponse.builder()
                .tenantId(tenantId)
                .fromDate(from)
                .toDate(to)
                .totalArchived(snapshots.size())
                .months(monthlyBuckets(snapshots))
                .byReferralSource(snapshots.stream().collect(Collectors.groupingBy(
                        s -> s.getReferralSource() != null ? s.getReferralSource() : "UNKNOWN", TreeMap::new, Collectors.counting())))
                .byGrade(snapshots.stream().collect(Collectors.groupingBy(ArchivedLeadSnapshot::getTargetGradeId, TreeMap::new,
                        Collectors.counting())))
                .build();
    }

    static List<ArchivedLeadTrendResponse.MonthBucket> monthlyBuckets(List<ArchivedLeadSnapshot> snapshots) {
        Map<String, List<ArchivedLeadSnapshot>> byMonth = snapshots.stream().collect(Collectors.groupingBy(
                s -> s.getClosedAt().atZone(REPORT_ZONE).format(MONTH), TreeMap::new, Collectors.toList()));
        List<ArchivedLeadTrendResponse.MonthBucket> buckets = new ArrayList<>();
        byMonth.forEach((month, list) -> {
            List<BigDecimal> rejectedScores = list.stream()
                    .filter(s -> EnquiryStatus.REJECTED.name().equals(s.getFinalStatus()) && s.getEntranceTestScore() != null)
                    .map(ArchivedLeadSnapshot::getEntranceTestScore).toList();
            BigDecimal average = rejectedScores.isEmpty() ? null
                    : rejectedScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(rejectedScores.size()), 2, RoundingMode.HALF_UP);
            buckets.add(ArchivedLeadTrendResponse.MonthBucket.builder()
                    .month(month)
                    .rejected(list.stream().filter(s -> EnquiryStatus.REJECTED.name().equals(s.getFinalStatus())).count())
                    .withdrawn(list.stream().filter(s -> EnquiryStatus.WITHDRAWN.name().equals(s.getFinalStatus())).count())
                    .averageRejectedEntranceScore(average)
                    .build());
        });
        return buckets;
    }

    private static Instant closedAt(AdmissionEnquiry e) {
        return e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt() != null ? e.getCreatedAt() : Instant.now();
    }
}
