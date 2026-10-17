package com.kvs.admission.service;

import com.kvs.admission.dto.ChannelConversionStat;
import com.kvs.admission.dto.ConversionRateReportResponse;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.core.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service computing lead conversion rates per marketing channel / referral source.
 * Covers user story:
 * US-02007: Track lead conversion rates across marketing channels and referral sources
 * Actor: Marketing Manager
 */
@Service
@Slf4j
public class LeadConversionAnalyticsService {

    static final int DEFAULT_LOOKBACK_DAYS = 365;
    static final int MAX_RANGE_DAYS = 731;

    private static final Set<String> QUALIFIED = Set.of(
            EnquiryStatus.EVALUATION_PASSED.name(), EnquiryStatus.OFFER_ISSUED.name(), EnquiryStatus.ENROLLED.name());
    private static final Set<String> OFFERED = Set.of(
            EnquiryStatus.OFFER_ISSUED.name(), EnquiryStatus.ENROLLED.name());
    private static final Set<String> LOST = Set.of(
            EnquiryStatus.REJECTED.name(), EnquiryStatus.WITHDRAWN.name());

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Builds the conversion report for leads created between {@code fromDate} and {@code toDate} (inclusive, UTC).
     * Defaults to the last 365 days; the range may not exceed two years.
     *
     * @param tenantId the tenant
     * @param branchId optional branch filter
     * @param fromDate optional start date
     * @param toDate   optional end date
     * @return per-channel funnel counts and conversion rates
     */
    @Transactional // not read-only: viewing the report writes an audit entry
    public ConversionRateReportResponse getConversionRates(String tenantId, String branchId,
                                                           LocalDate fromDate, LocalDate toDate) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BadRequestException("Tenant ID is required");
        }
        LocalDate to = toDate != null ? toDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_LOOKBACK_DAYS);
        if (from.isAfter(to)) {
            throw new BadRequestException("fromDate must not be after toDate");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BadRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        log.info("Computing lead conversion rates for tenant: {}, branch: {}, from {} to {}", tenantId, branchId, from, to);

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Object[]> rows = StringUtils.hasText(branchId)
                ? admissionEnquiryRepository.countByReferralSourceAndStatusForBranch(tenantId, branchId, fromInstant, toExclusive)
                : admissionEnquiryRepository.countByReferralSourceAndStatus(tenantId, fromInstant, toExclusive);

        List<ChannelConversionStat> channels = aggregate(rows);
        long totalLeads = channels.stream().mapToLong(ChannelConversionStat::getTotalLeads).sum();
        long enrolled = channels.stream().mapToLong(ChannelConversionStat::getEnrolledLeads).sum();

        ConversionRateReportResponse report = ConversionRateReportResponse.builder()
                .tenantId(tenantId)
                .branchId(StringUtils.hasText(branchId) ? branchId : null)
                .fromDate(from)
                .toDate(to)
                .totalLeads(totalLeads)
                .enrolledLeads(enrolled)
                .overallConversionRate(rate(enrolled, totalLeads))
                .topChannel(channels.isEmpty() || channels.get(0).getEnrolledLeads() == 0
                        ? null
                        : channels.get(0).getReferralSource())
                .channels(channels)
                .generatedAt(Instant.now())
                .build();

        auditService.logSuccess("lead_analytics:" + tenantId, "VIEW_LEAD_CONVERSION_REPORT",
                "Generated conversion report for " + from + " to " + to
                        + (report.getBranchId() != null ? " | Branch: " + report.getBranchId() : "")
                        + " | Leads: " + totalLeads + " | Enrolled: " + enrolled
                        + " | Rate: " + report.getOverallConversionRate() + "%");
        return report;
    }

    /**
     * Folds [referralSource, status, count] rows into one funnel per channel, ordered by conversion rate
     * (then lead volume) descending.
     */
    static List<ChannelConversionStat> aggregate(List<Object[]> rows) {
        Map<String, long[]> byChannel = new TreeMap<>();
        for (Object[] row : rows) {
            String channel = StringUtils.hasText((String) row[0]) ? ((String) row[0]).toUpperCase(Locale.ROOT) : "UNKNOWN";
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            // [total, qualified, offered, enrolled, lost]
            long[] totals = byChannel.computeIfAbsent(channel, k -> new long[5]);
            totals[0] += count;
            if (QUALIFIED.contains(status)) {
                totals[1] += count;
            }
            if (OFFERED.contains(status)) {
                totals[2] += count;
            }
            if (EnquiryStatus.ENROLLED.name().equals(status)) {
                totals[3] += count;
            }
            if (LOST.contains(status)) {
                totals[4] += count;
            }
        }
        List<ChannelConversionStat> stats = new ArrayList<>();
        byChannel.forEach((channel, t) -> stats.add(ChannelConversionStat.builder()
                .referralSource(channel)
                .totalLeads(t[0])
                .qualifiedLeads(t[1])
                .offersIssued(t[2])
                .enrolledLeads(t[3])
                .lostLeads(t[4])
                .conversionRate(rate(t[3], t[0]))
                .build()));
        stats.sort(Comparator.comparing(ChannelConversionStat::getConversionRate).reversed()
                .thenComparing(Comparator.comparingLong(ChannelConversionStat::getTotalLeads).reversed()));
        return stats;
    }

    static BigDecimal rate(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
