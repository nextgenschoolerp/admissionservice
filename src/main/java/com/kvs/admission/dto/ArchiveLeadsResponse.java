package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of an archive run (US-02020).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArchiveLeadsResponse {
    private UUID archiveBatchId;
    private int archived;
    private int rejected;
    private int withdrawn;
    /** Closed leads skipped because they closed less than minimumDaysClosed days ago. */
    private int tooRecent;
    private Instant cutoff;
    private List<String> applicationNumbers;
}
