package com.kvs.admission.repo;

import com.kvs.admission.entity.TenantIdFormatConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantIdFormatConfigRepository extends JpaRepository<TenantIdFormatConfig, Long> {
    Optional<TenantIdFormatConfig> findByTenantIdAndFormatKey(String tenantId, String formatKey);
}
