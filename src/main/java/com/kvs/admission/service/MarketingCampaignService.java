package com.kvs.admission.service;

import com.kvs.admission.dto.CampaignResponse;
import com.kvs.admission.dto.CostPerAcquisitionReportResponse;
import com.kvs.admission.dto.CreateCampaignRequest;
import com.kvs.admission.dto.UpdateCampaignRequest;
import com.kvs.admission.entity.AdmissionEnquiry;
import com.kvs.admission.entity.MarketingCampaign;
import com.kvs.admission.enums.CampaignStatus;
import com.kvs.admission.enums.EnquiryStatus;
import com.kvs.admission.repo.AdmissionEnquiryRepository;
import com.kvs.admission.repo.MarketingCampaignRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for marketing campaigns and their cost-per-acquisition metrics.
 * Covers user stories:
 * US-02019, US-02039, US-02059, US-02079, US-02099: Analyze cost-per-acquisition metrics across active marketing campaigns
 * Actor: Marketing Manager (US-02039/79). The Registrar (US-02019/59/99) is not granted.
 */
@Service
@Slf4j
public class MarketingCampaignService {

    private static final Set<String> QUALIFIED = Set.of(
            EnquiryStatus.EVALUATION_PASSED.name(), EnquiryStatus.OFFER_ISSUED.name(), EnquiryStatus.ENROLLED.name());

    @Autowired
    private MarketingCampaignRepository campaignRepository;

    @Autowired
    private AdmissionEnquiryRepository admissionEnquiryRepository;

    @Autowired
    private AdmissionAuditService auditService;

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must not be before the start date");
        }
        if (campaignRepository.findByTenantIdAndCampaignCode(request.getTenantId(), request.getCampaignCode()).isPresent()) {
            throw new ConflictException("Campaign code " + request.getCampaignCode() + " already exists");
        }
        MarketingCampaign campaign = new MarketingCampaign();
        campaign.setTenantId(request.getTenantId());
        campaign.setCampaignCode(request.getCampaignCode());
        campaign.setCampaignName(request.getCampaignName());
        campaign.setChannel(request.getChannel());
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setBudgetAmount(request.getBudgetAmount());
        campaign.setSpendAmount(BigDecimal.ZERO.setScale(2));
        campaign.setStatus(CampaignStatus.ACTIVE);
        MarketingCampaign saved = campaignRepository.save(campaign);
        auditService.logSuccess("marketing_campaign:" + request.getTenantId(), "CREATE_MARKETING_CAMPAIGN",
                "Created " + saved.getCampaignCode() + " (" + saved.getChannel() + ") budget INR " + saved.getBudgetAmount().toPlainString()
                        + " from " + saved.getStartDate() + " to " + saved.getEndDate());
        return map(saved);
    }

    @Transactional
    public CampaignResponse updateCampaign(UUID campaignId, UpdateCampaignRequest request) {
        MarketingCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("MarketingCampaign", "campaignId", campaignId));
        if (request.getSpendAmount() == null && request.getStatus() == null) {
            throw new BadRequestException("Provide spendAmount and/or status");
        }
        if (request.getSpendAmount() != null) {
            campaign.setSpendAmount(request.getSpendAmount());
        }
        if (request.getStatus() != null) {
            campaign.setStatus(request.getStatus());
        }
        MarketingCampaign saved = campaignRepository.save(campaign);
        auditService.logSuccess("marketing_campaign:" + saved.getTenantId(), "UPDATE_MARKETING_CAMPAIGN",
                saved.getCampaignCode() + " | Spend: INR " + saved.getSpendAmount().toPlainString() + " | Status: " + saved.getStatus());
        return map(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listCampaigns(String tenantId, CampaignStatus status) {
        List<MarketingCampaign> campaigns = status != null
                ? campaignRepository.findByTenantIdAndStatusOrderByStartDateDesc(tenantId, status)
                : campaignRepository.findByTenantIdOrderByStartDateDesc(tenantId);
        return campaigns.stream().map(this::map).toList();
    }

    /**
     * Cost per lead and per acquisition for each campaign (default: ACTIVE campaigns). A lead is attributed
     * to a campaign through the enquiry's campaign code; an acquisition is an ENROLLED lead.
     */
    @Transactional // not read-only: viewing the report writes an audit entry
    public CostPerAcquisitionReportResponse costPerAcquisition(String tenantId, CampaignStatus status) {
        CampaignStatus filter = status != null ? status : CampaignStatus.ACTIVE;
        List<MarketingCampaign> campaigns = campaignRepository.findByTenantIdAndStatusOrderByStartDateDesc(tenantId, filter);
        Map<String, List<AdmissionEnquiry>> leadsByCampaign = campaigns.isEmpty() ? Map.of()
                : admissionEnquiryRepository.findByTenantIdAndCampaignCodeIn(tenantId,
                        campaigns.stream().map(MarketingCampaign::getCampaignCode).toList())
                .stream().collect(Collectors.groupingBy(AdmissionEnquiry::getCampaignCode));

        List<CampaignResponse> metrics = new ArrayList<>();
        for (MarketingCampaign c : campaigns) {
            List<AdmissionEnquiry> leads = leadsByCampaign.getOrDefault(c.getCampaignCode(), List.of());
            long qualified = leads.stream().filter(e -> QUALIFIED.contains(e.getEnquiryStatus())).count();
            long enrolled = leads.stream().filter(e -> EnquiryStatus.ENROLLED.name().equals(e.getEnquiryStatus())).count();
            CampaignResponse r = map(c);
            r.setLeads((long) leads.size());
            r.setQualifiedLeads(qualified);
            r.setEnrolledLeads(enrolled);
            r.setConversionRate(LeadConversionAnalyticsService.rate(enrolled, leads.size()));
            r.setCostPerLead(divide(c.getSpendAmount(), leads.size()));
            r.setCostPerAcquisition(divide(c.getSpendAmount(), enrolled));
            r.setBudgetUtilisation(c.getSpendAmount().multiply(BigDecimal.valueOf(100))
                    .divide(c.getBudgetAmount(), 2, RoundingMode.HALF_UP));
            r.setOverBudget(c.getSpendAmount().compareTo(c.getBudgetAmount()) > 0);
            metrics.add(r);
        }
        metrics.sort(Comparator.comparing(CampaignResponse::getCostPerAcquisition, Comparator.nullsLast(Comparator.naturalOrder())));

        BigDecimal totalSpend = campaigns.stream().map(MarketingCampaign::getSpendAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalLeads = metrics.stream().mapToLong(CampaignResponse::getLeads).sum();
        long totalEnrolled = metrics.stream().mapToLong(CampaignResponse::getEnrolledLeads).sum();
        CostPerAcquisitionReportResponse report = CostPerAcquisitionReportResponse.builder()
                .tenantId(tenantId)
                .statusFilter(filter.name())
                .campaigns(metrics.size())
                .totalSpend(totalSpend)
                .totalLeads(totalLeads)
                .totalEnrolled(totalEnrolled)
                .blendedCostPerAcquisition(divide(totalSpend, totalEnrolled))
                .mostEfficientCampaign(metrics.stream().filter(m -> m.getCostPerAcquisition() != null)
                        .map(CampaignResponse::getCampaignCode).findFirst().orElse(null))
                .campaignMetrics(metrics)
                .generatedAt(Instant.now())
                .build();
        auditService.logSuccess("marketing_campaign:" + tenantId, "VIEW_COST_PER_ACQUISITION_REPORT",
                filter + " campaigns: " + metrics.size() + " | Spend: INR " + totalSpend.toPlainString() + " | Leads: " + totalLeads
                        + " | Enrolled: " + totalEnrolled + " | Blended CPA: " + report.getBlendedCostPerAcquisition());
        return report;
    }

    /** amount / count rounded to 2 decimals, or null when count is zero. */
    static BigDecimal divide(BigDecimal amount, long count) {
        return count == 0 ? null : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private CampaignResponse map(MarketingCampaign c) {
        return CampaignResponse.builder()
                .campaignId(c.getCampaignId())
                .tenantId(c.getTenantId())
                .campaignCode(c.getCampaignCode())
                .campaignName(c.getCampaignName())
                .channel(c.getChannel())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .budgetAmount(c.getBudgetAmount())
                .spendAmount(c.getSpendAmount())
                .currency(c.getCurrency())
                .status(c.getStatus().name())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
