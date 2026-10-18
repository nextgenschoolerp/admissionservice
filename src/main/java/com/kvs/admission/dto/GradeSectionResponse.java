package com.kvs.admission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GradeSectionResponse {
    private UUID sectionId;
    private String tenantId;
    private String branchId;
    private String academicYearId;
    private String gradeId;
    private String sectionCode;
    private String classTeacherName;
    private int capacity;
    private long placedStudents;
    private long availableSeats;
}
