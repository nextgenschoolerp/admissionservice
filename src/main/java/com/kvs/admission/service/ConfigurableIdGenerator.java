package com.kvs.admission.service;

import com.kvs.admission.repo.TenantIdFormatConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Generates auto-numbered identifiers using per-tenant configuration from tenant_id_format_config.
 * Falls back to the original hardcoded format when no configuration row exists.
 */
@Component
@RequiredArgsConstructor
public class ConfigurableIdGenerator {

    private static final Map<String, String> PREFIX_TO_FORMAT_KEY = Map.of(
            "ADM", "admission_number",
            "APP", "application_number",
            "OFFER", "offer_code",
            "EXAM", "exam_code",
            "ADMIT", "admit_card"
    );

    private final TenantIdFormatConfigRepository configRepository;

    /**
     * Generates an ID for the given tenant, looking up the format_key that matches the legacy prefix.
     * IDs whose prefix has no config row (e.g. MERIT, RCPT in admissionservice) keep the hardcoded format.
     */
    public String next(String tenantId, String legacyPrefix) {
        String formatKey = PREFIX_TO_FORMAT_KEY.get(legacyPrefix);
        if (formatKey == null) {
            return ReferenceNumberGenerator.next(legacyPrefix);
        }

        var config = configRepository.findByTenantIdAndFormatKey(tenantId, formatKey).orElse(null);
        if (config == null) {
            return ReferenceNumberGenerator.next(legacyPrefix);
        }

        String sep = config.getSeparator();
        StringBuilder sb = new StringBuilder(config.getPrefix());
        if (config.isIncludeYear()) {
            sb.append(sep).append(LocalDate.now().getYear());
        }
        sb.append(sep);
        if ("RANDOM".equals(config.getCounterMode())) {
            sb.append(UUID.randomUUID().toString().replace("-", "")
                    .substring(0, config.getCounterLength()).toUpperCase());
        } else {
            sb.append(String.format("%0" + config.getCounterLength() + "d", config.getNextValue()));
        }
        return sb.toString();
    }

    /** Application number: uses the application_number format key. */
    public String generateApplicationNumber(String tenantId) {
        return next(tenantId, "APP");
    }
}
