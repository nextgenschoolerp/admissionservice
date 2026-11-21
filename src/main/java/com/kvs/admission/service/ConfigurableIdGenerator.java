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
 * SEQUENTIAL numbers come from the stored counter of the school, format and year ({@link IdCounterService}); a number
 * already used (entered by hand, or under the same prefix before) is skipped.
 */
@Component
@RequiredArgsConstructor
public class ConfigurableIdGenerator {

    /** Legacy prefix → format key, and where the IDs of that format are stored (entity, field). */
    private record Format(String key, String entity, String field) {
    }

    private static final Map<String, Format> FORMATS = Map.of(
            "ADM", new Format("admission_number", "StudentMasterRecord", "admissionNumber"),
            "APP", new Format("application_number", "AdmissionEnquiry", "applicationNumber"),
            "OFFER", new Format("offer_code", "AdmissionOffer", "offerCode"),
            "EXAM", new Format("exam_code", "EntranceExamSchedule", "examCode"),
            "ADMIT", new Format("admit_card", "AdmitCard", "admitCardNumber")
    );

    /** Numbers skipped at most in one go because they are already used, before giving up. */
    private static final int MAX_SKIPS = 10_000;

    private final TenantIdFormatConfigRepository configRepository;
    private final IdCounterService counters;

    /**
     * Generates an ID for the given tenant, looking up the format_key that matches the legacy prefix.
     * IDs whose prefix has no config row (e.g. MERIT, RCPT in admissionservice) keep the hardcoded format.
     */
    public String next(String tenantId, String legacyPrefix) {
        Format format = FORMATS.get(legacyPrefix);
        if (format == null) {
            return ReferenceNumberGenerator.next(legacyPrefix);
        }

        var config = configRepository.findByTenantIdAndFormatKey(tenantId, format.key()).orElse(null);
        if (config == null) {
            return ReferenceNumberGenerator.next(legacyPrefix);
        }

        String sep = config.getSeparator();
        int year = LocalDate.now().getYear();
        StringBuilder sb = new StringBuilder(config.getPrefix());
        if (config.isIncludeYear()) {
            sb.append(sep).append(year);
        }
        sb.append(sep);
        String start = sb.toString();
        if ("RANDOM".equals(config.getCounterMode())) {
            return start + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, config.getCounterLength()).toUpperCase();
        }

        int counterYear = config.isIncludeYear() ? year : IdCounterService.NO_YEAR;
        for (int i = 0; i < MAX_SKIPS; i++) {
            long n = counters.next(tenantId, format.key(), counterYear,
                    () -> counters.highestIssued(format.entity(), format.field(), true, tenantId, start));
            String id = start + String.format("%0" + config.getCounterLength() + "d", n);
            if (!counters.isIssued(format.entity(), format.field(), true, tenantId, id)) {
                return id;
            }
        }
        throw new IllegalStateException("No free " + format.key() + " number after " + MAX_SKIPS + " tries");
    }

    /** Application number: uses the application_number format key. */
    public String generateApplicationNumber(String tenantId) {
        return next(tenantId, "APP");
    }
}
