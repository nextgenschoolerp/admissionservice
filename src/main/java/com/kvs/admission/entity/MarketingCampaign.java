package com.kvs.admission.entity;

import com.kvs.admission.enums.CampaignStatus;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A recruitment marketing campaign with its budget and actual spend. Leads are attributed to a campaign
 * through {@code AdmissionEnquiry#getCampaignCode()}.
 * Covers: US-02019 — Analyze cost-per-acquisition metrics across active marketing campaigns
 * Actor: Marketing Manager
 */
@Entity
@Table(name = "marketing_campaigns", uniqueConstraints = {
        @UniqueConstraint(name = "uk_campaigns_tenant_code", columnNames = {"tenant_id", "campaign_code"})
}, indexes = {
        @Index(name = "idx_campaigns_tenant_status", columnList = "tenant_id, status")
})
@Getter
@Setter
public class MarketingCampaign extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, unique = true, updatable = false)
    private UUID campaignId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "campaign_code", nullable = false, length = 50)
    private String campaignCode;

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    /** Marketing channel, aligned with enquiry referral sources (WEBSITE, SOCIAL_MEDIA, REFERRAL, ...). */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "budget_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "spend_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal spendAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.ACTIVE;

    @PrePersist
    public void prePersist() {
        if (this.campaignId == null) {
            this.campaignId = UUID.randomUUID();
        }
    }
}
