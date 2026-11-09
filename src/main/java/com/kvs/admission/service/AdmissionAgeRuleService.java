package com.kvs.admission.service;

import com.kvs.admission.dto.AgeCheckResponse;
import com.kvs.admission.dto.AgeRuleResponse;
import com.kvs.admission.dto.SaveAgeRuleRequest;
import com.kvs.admission.entity.AdmissionAgeRule;
import com.kvs.admission.repo.AdmissionAgeRuleRepository;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The minimum age for admission to a class, per board. A school's own rule for its campus board replaces the master rule of that
 * board (a state board falls back to the STATE rules). The age is counted on the cut-off date of the admission year.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdmissionAgeRuleService {

    private final AdmissionAgeRuleRepository repository;

    /** The rules in force for a board: one per class, the school's own where it has set one. */
    @Transactional(readOnly = true)
    public List<AgeRuleResponse> effective(String tenantId, String boardCode) {
        String board = normalize(boardCode);
        Map<String, AgeRuleResponse> byClass = new LinkedHashMap<>();
        List<AdmissionAgeRule> visible = repository.findVisible(tenantId);
        // master rules first (the STATE ones for a state board, then the board's own), then the school's overrides
        for (AdmissionAgeRule r : visible) if (r.isMaster() && "STATE".equals(r.getBoardCode()) && board.startsWith("STATE")) byClass.put(key(r), map(r, board));
        for (AdmissionAgeRule r : visible) if (r.isMaster() && r.getBoardCode().equals(board)) byClass.put(key(r), map(r, board));
        for (AdmissionAgeRule r : visible) if (!r.isMaster() && r.getBoardCode().equals(board)) byClass.put(key(r), map(r, board));
        return new ArrayList<>(byClass.values());
    }

    /** Save the school's own rule for a class of a board. */
    public AgeRuleResponse save(String boardCode, String className, SaveAgeRuleRequest request) {
        String board = normalize(boardCode);
        String cls = className.trim();
        if (cls.isEmpty() || cls.length() > 50) throw new BadRequestException("Give the class name (at most 50 characters)");
        try {
            java.time.MonthDay.of(request.getCutoffMonth(), request.getCutoffDay());
        } catch (DateTimeException e) {
            throw new BadRequestException("The cut-off day is not a date: " + request.getCutoffDay() + "/" + request.getCutoffMonth());
        }
        AdmissionAgeRule r = repository.findByTenantIdAndBoardCodeAndClassNameIgnoreCase(request.getTenantId(), board, cls).orElseGet(() -> {
            AdmissionAgeRule n = new AdmissionAgeRule();
            n.setTenantId(request.getTenantId());
            n.setBoardCode(board);
            n.setClassName(cls);
            return n;
        });
        r.setMinAgeYears(request.getMinAgeYears());
        r.setCutoffMonth(request.getCutoffMonth());
        r.setCutoffDay(request.getCutoffDay());
        r.setNeedsReview(false);
        return map(repository.save(r), board);
    }

    /** Remove the school's own rule: the board's master rule applies again. */
    public void reset(String tenantId, String boardCode, String className) {
        AdmissionAgeRule r = repository.findByTenantIdAndBoardCodeAndClassNameIgnoreCase(tenantId, normalize(boardCode), className.trim())
                .orElseThrow(() -> new ResourceNotFoundException("AdmissionAgeRule", "className", className));
        repository.delete(r);
    }

    /** Whether a child born on dateOfBirth meets the minimum age for a class in an admission year ("2026-2027" or "2026"). */
    @Transactional(readOnly = true)
    public AgeCheckResponse check(String tenantId, String boardCode, String className, LocalDate dateOfBirth, String academicYear) {
        String board = normalize(boardCode);
        AgeRuleResponse rule = effective(tenantId, board).stream().filter(r -> r.getClassName().equalsIgnoreCase(className.trim())).findFirst().orElse(null);
        if (rule == null) {
            return AgeCheckResponse.builder().ruleFound(false).className(className.trim())
                    .message("No age rule is set for " + className.trim() + " under " + board).build();
        }
        Matcher m = Pattern.compile("(\\d{4})").matcher(academicYear == null ? "" : academicYear);
        if (!m.find()) throw new BadRequestException("The academic year must start with a year, e.g. 2026-2027");
        LocalDate cutoff;
        try {
            cutoff = LocalDate.of(Integer.parseInt(m.group(1)), rule.getCutoffMonth(), rule.getCutoffDay());
        } catch (DateTimeException e) {
            cutoff = LocalDate.of(Integer.parseInt(m.group(1)), rule.getCutoffMonth(), 1).withDayOfMonth(
                    LocalDate.of(Integer.parseInt(m.group(1)), rule.getCutoffMonth(), 1).lengthOfMonth());
        }
        LocalDate latest = cutoff.minusYears(rule.getMinAgeYears());
        boolean eligible = !dateOfBirth.isAfter(latest);
        Period age = dateOfBirth.isAfter(cutoff) ? Period.ZERO : Period.between(dateOfBirth, cutoff);
        String cutoffText = cutoff.getDayOfMonth() + " " + cutoff.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + cutoff.getYear();
        return AgeCheckResponse.builder().ruleFound(true).eligible(eligible).className(rule.getClassName()).minAgeYears(rule.getMinAgeYears())
                .cutoffDate(cutoff).ageYears(age.getYears()).ageMonths(age.getMonths()).latestDateOfBirth(latest).source(rule.getSource())
                .message(eligible ? "Meets the minimum age of " + rule.getMinAgeYears() + " for " + rule.getClassName() + " on " + cutoffText
                        : "Below the minimum age of " + rule.getMinAgeYears() + " for " + rule.getClassName() + " on " + cutoffText
                        + " (" + age.getYears() + " years " + age.getMonths() + " months)").build();
    }

    // ------------------------------------------------------------------ helpers

    private static String normalize(String boardCode) {
        if (boardCode == null || boardCode.isBlank()) throw new BadRequestException("boardCode is required");
        return boardCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String key(AdmissionAgeRule r) {
        return r.getClassName().toLowerCase(Locale.ROOT);
    }

    private AgeRuleResponse map(AdmissionAgeRule r, String board) {
        return AgeRuleResponse.builder().boardCode(board).className(r.getClassName()).minAgeYears(r.getMinAgeYears())
                .cutoffMonth(r.getCutoffMonth()).cutoffDay(r.getCutoffDay()).source(r.isMaster() ? "MASTER" : "SCHOOL")
                .needsReview(r.isNeedsReview()).build();
    }
}
