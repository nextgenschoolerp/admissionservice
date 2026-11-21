package com.kvs.admission.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "tenant_id_format_config")
@Getter
public class TenantIdFormatConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "format_key", nullable = false)
    private String formatKey;

    @Column(name = "prefix", nullable = false)
    private String prefix;

    @Column(name = "separator", nullable = false)
    private String separator;

    @Column(name = "include_year", nullable = false)
    private boolean includeYear;

    @Column(name = "counter_mode", nullable = false)
    private String counterMode;

    @Column(name = "counter_length", nullable = false)
    private int counterLength;

    @Column(name = "next_value", nullable = false)
    private long nextValue;
}
