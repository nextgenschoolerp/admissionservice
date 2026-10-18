package com.kvs.admission.service;

import com.kvs.admission.dto.*;
import com.kvs.admission.entity.GradeSection;
import com.kvs.admission.entity.StudentMasterRecord;
import com.kvs.admission.repo.GradeSectionRepository;
import com.kvs.admission.repo.StudentMasterRecordRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service placing confirmed admissions into grade sections.
 * Covers user stories:
 * US-02013, US-02033, US-02053, US-02073, US-02093: Assign target grade levels and sections to confirmed admission leads
 * Placement is done by the Admission Officer or Registrar; parents see the result on their child's Student Master record.
 */
@Service
@Slf4j
public class SectionPlacementService {

    private static final String ACTIVE = "ACTIVE";

    @Autowired
    private GradeSectionRepository sectionRepository;

    @Autowired
    private StudentMasterRecordRepository studentRepository;

    @Autowired
    private AdmissionAuditService auditService;

    /**
     * Creates or updates the listed sections of a grade. A capacity may not drop below the students already placed.
     */
    @Transactional
    public List<GradeSectionResponse> configureSections(ConfigureGradeSectionsRequest request) {
        Set<String> codes = new HashSet<>();
        for (ConfigureGradeSectionsRequest.SectionInput input : request.getSections()) {
            if (!codes.add(input.getSectionCode())) {
                throw new BadRequestException("Section " + input.getSectionCode() + " is listed more than once");
            }
        }
        for (ConfigureGradeSectionsRequest.SectionInput input : request.getSections()) {
            GradeSection section = sectionRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndSectionCode(
                    request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId(),
                    input.getSectionCode()).orElseGet(GradeSection::new);
            long placed = section.getId() == null ? 0 : countPlaced(section);
            if (input.getCapacity() < placed) {
                throw new ConflictException("Section " + input.getSectionCode() + " already has " + placed
                        + " students; capacity cannot be reduced to " + input.getCapacity());
            }
            section.setTenantId(request.getTenantId());
            section.setBranchId(request.getBranchId());
            section.setAcademicYearId(request.getAcademicYearId());
            section.setGradeId(request.getGradeId());
            section.setSectionCode(input.getSectionCode());
            section.setCapacity(input.getCapacity());
            section.setClassTeacherName(input.getClassTeacherName());
            sectionRepository.save(section);
        }
        auditService.logSuccess("grade_section:" + request.getTenantId(), "CONFIGURE_GRADE_SECTIONS",
                "Grade " + request.getGradeId() + " (" + request.getAcademicYearId() + ", " + request.getBranchId() + ") sections: "
                        + request.getSections().stream().map(s -> s.getSectionCode() + "=" + s.getCapacity()).collect(Collectors.joining(", ")));
        return listSections(request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId());
    }

    /**
     * Lists the sections of a grade with current occupancy.
     */
    @Transactional(readOnly = true)
    public List<GradeSectionResponse> listSections(String tenantId, String branchId, String academicYearId, String gradeId) {
        return sectionRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdOrderBySectionCodeAsc(
                        tenantId, branchId, academicYearId, gradeId).stream()
                .map(s -> mapSection(s, countPlaced(s)))
                .toList();
    }

    /**
     * Places every active student of the grade who has no section yet. Students are taken in alphabetical
     * order (last name, first name) and each goes to the section with the most free seats.
     */
    @Transactional
    public AutoPlacementResponse autoPlace(AutoPlacementRequest request) {
        List<GradeSection> sections = sectionRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdOrderBySectionCodeAsc(
                request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId());
        if (sections.isEmpty()) {
            throw new BadRequestException("No sections are configured for grade " + request.getGradeId()
                    + " in " + request.getAcademicYearId());
        }
        List<StudentMasterRecord> unplaced = studentRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndStatus(
                        request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId(), ACTIVE)
                .stream()
                .filter(s -> s.getSectionCode() == null)
                .sorted(Comparator.comparing(StudentMasterRecord::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(StudentMasterRecord::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, Integer> freeSeats = new LinkedHashMap<>();
        sections.forEach(s -> freeSeats.put(s.getSectionCode(), (int) (s.getCapacity() - countPlaced(s))));
        List<String> assignment = assignBalanced(freeSeats, unplaced.size());

        List<AutoPlacementResponse.Placement> placements = new ArrayList<>();
        for (int i = 0; i < unplaced.size(); i++) {
            String code = assignment.get(i);
            if (code == null) {
                continue;
            }
            StudentMasterRecord student = unplaced.get(i);
            student.setSectionCode(code);
            studentRepository.save(student);
            placements.add(AutoPlacementResponse.Placement.builder()
                    .studentId(student.getStudentId())
                    .admissionNumber(student.getAdmissionNumber())
                    .studentName(student.getFirstName() + " " + student.getLastName())
                    .sectionCode(code)
                    .build());
        }
        int left = unplaced.size() - placements.size();
        auditService.logSuccess("grade_section:" + request.getTenantId(), "AUTO_PLACE_STUDENTS",
                "Grade " + request.getGradeId() + " (" + request.getAcademicYearId() + ") | Placed: " + placements.size()
                        + " | Unplaced (sections full): " + left);
        return AutoPlacementResponse.builder()
                .gradeId(request.getGradeId())
                .placed(placements.size())
                .unplaced(left)
                .placements(placements)
                .sections(listSections(request.getTenantId(), request.getBranchId(), request.getAcademicYearId(), request.getGradeId()))
                .build();
    }

    /**
     * Places (or moves) one student into a grade section. The section must exist in the student's
     * branch and academic year and have a free seat.
     */
    @Transactional
    public StudentMasterRecordResponse placeStudent(UUID studentId, PlaceStudentRequest request) {
        StudentMasterRecord student = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentMasterRecord", "studentId", studentId));
        if (!ACTIVE.equals(student.getStatus())) {
            throw new BadRequestException("Only active students can be placed; student is " + student.getStatus());
        }
        GradeSection section = sectionRepository.findByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndSectionCode(
                        student.getTenantId(), student.getBranchId(), student.getAcademicYearId(), request.getGradeId(),
                        request.getSectionCode())
                .orElseThrow(() -> new BadRequestException("Section " + request.getGradeId() + "-" + request.getSectionCode()
                        + " is not configured for " + student.getAcademicYearId()));
        boolean alreadyThere = request.getGradeId().equals(student.getGradeId()) && request.getSectionCode().equals(student.getSectionCode());
        if (!alreadyThere && countPlaced(section) >= section.getCapacity()) {
            throw new ConflictException("Section " + request.getGradeId() + "-" + request.getSectionCode() + " is full ("
                    + section.getCapacity() + " seats)");
        }
        String previous = student.getGradeId() + "-" + (student.getSectionCode() != null ? student.getSectionCode() : "unplaced");
        student.setGradeId(request.getGradeId());
        student.setSectionCode(request.getSectionCode());
        StudentMasterRecord saved = studentRepository.save(student);
        auditService.logSuccess("grade_section:" + saved.getTenantId(), "PLACE_STUDENT_IN_SECTION",
                "Student " + saved.getAdmissionNumber() + " moved from " + previous + " to "
                        + request.getGradeId() + "-" + request.getSectionCode());
        return StudentMasterRecordResponse.builder()
                .studentId(saved.getStudentId())
                .admissionNumber(saved.getAdmissionNumber())
                .tenantId(saved.getTenantId())
                .branchId(saved.getBranchId())
                .enquiryId(saved.getEnquiryId())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .dateOfBirth(saved.getDateOfBirth())
                .gender(saved.getGender().name())
                .gradeId(saved.getGradeId())
                .academicYearId(saved.getAcademicYearId())
                .guardianName(saved.getGuardianName())
                .guardianEmail(saved.getGuardianEmail())
                .guardianPhone(saved.getGuardianPhone())
                .status(saved.getStatus())
                .sectionCode(saved.getSectionCode())
                .convertedBy(saved.getConvertedBy())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * Assigns {@code students} seats one by one, each to the section with the most free seats
     * (ties broken by section order). Returns one section code per student, or null when everything is full.
     */
    static List<String> assignBalanced(Map<String, Integer> freeSeats, int students) {
        Map<String, Integer> free = new LinkedHashMap<>(freeSeats);
        List<String> result = new ArrayList<>(students);
        for (int i = 0; i < students; i++) {
            String best = null;
            for (Map.Entry<String, Integer> e : free.entrySet()) {
                if (e.getValue() > 0 && (best == null || e.getValue() > free.get(best))) {
                    best = e.getKey();
                }
            }
            if (best != null) {
                free.merge(best, -1, Integer::sum);
            }
            result.add(best);
        }
        return result;
    }

    private long countPlaced(GradeSection s) {
        return studentRepository.countByTenantIdAndBranchIdAndAcademicYearIdAndGradeIdAndSectionCode(
                s.getTenantId(), s.getBranchId(), s.getAcademicYearId(), s.getGradeId(), s.getSectionCode());
    }

    private GradeSectionResponse mapSection(GradeSection s, long placed) {
        return GradeSectionResponse.builder()
                .sectionId(s.getSectionId())
                .tenantId(s.getTenantId())
                .branchId(s.getBranchId())
                .academicYearId(s.getAcademicYearId())
                .gradeId(s.getGradeId())
                .sectionCode(s.getSectionCode())
                .classTeacherName(s.getClassTeacherName())
                .capacity(s.getCapacity())
                .placedStudents(placed)
                .availableSeats(Math.max(0, s.getCapacity() - placed))
                .build();
    }
}
