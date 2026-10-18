package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of an automatic section placement run (US-02013).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoPlacementResponse {
    private String gradeId;
    private int placed;
    /** Students left without a section because every section is full. */
    private int unplaced;
    private List<Placement> placements;
    private List<GradeSectionResponse> sections;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Placement {
        private UUID studentId;
        private String admissionNumber;
        private String studentName;
        private String sectionCode;
    }
}
