package com.kvs.admission.repo;

import com.kvs.admission.entity.MarketingCampaign;
import com.kvs.admission.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, Long> {

    Optional<MarketingCampaign> findByCampaignId(UUID campaignId);

    Optional<MarketingCampaign> findByTenantIdAndCampaignCode(String tenantId, String campaignCode);

    List<MarketingCampaign> findByTenantIdOrderByStartDateDesc(String tenantId);

    List<MarketingCampaign> findByTenantIdAndStatusOrderByStartDateDesc(String tenantId, CampaignStatus status);
}
