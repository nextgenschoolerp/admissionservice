package com.kvs.admission.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Last number issued for one SEQUENTIAL ID format (Settings → General → ID formats) of a school in a calendar year
 * ({@code counterYear} 0 when the format has no year, so its numbers never restart). Lives in each school schema.
 * Read and advanced only through {@code IdCounterService}, which locks the row while it takes a number.
 */
@Entity
@Table(name = "id_counters")
@IdClass(IdCounter.Key.class)
@Getter
@Setter
public class IdCounter {

    @Id
    @Column(name = "tenant_id", length = 100, nullable = false)
    private String tenantId;

    @Id
    @Column(name = "format_key", length = 50, nullable = false)
    private String formatKey;

    @Id
    @Column(name = "counter_year", nullable = false)
    private int counterYear;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Primary key: school, format key and year. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Key implements Serializable {
        private String tenantId;
        private String formatKey;
        private int counterYear;
    }
}
