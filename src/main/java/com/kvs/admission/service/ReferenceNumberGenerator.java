package com.kvs.admission.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds human-readable reference numbers such as {@code EXAM-2026-1A2B3C4D}.
 */
final class ReferenceNumberGenerator {

    private ReferenceNumberGenerator() {
    }

    static String next(String prefix) {
        String uniquePart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + "-" + LocalDate.now().getYear() + "-" + uniquePart;
    }
}
