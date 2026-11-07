package com.kvs.admission.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.admission.entity.AdmissionAgeRule;
import com.kvs.admission.repo.AdmissionAgeRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * Start-up work for the board-aware admission rules, safe to run on every start:
 * - loads the master age rules of master/admission-age-rules.json that are missing from admission_age_rules;
 * - frees tc_verifications.board from the old four-board CHECK constraint (the board is now a code of the education boards master
 *   list) and renames the old STATE_BOARD value to STATE_OTHER.
 */
@Slf4j
@Component
public class AdmissionMasterDataLoader implements ApplicationRunner {

    private final AdmissionAgeRuleRepository rules;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public AdmissionMasterDataLoader(AdmissionAgeRuleRepository rules, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.rules = rules;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try {
            jdbc.execute("ALTER TABLE IF EXISTS tc_verifications DROP CONSTRAINT IF EXISTS tc_verifications_board_check");
            jdbc.update("UPDATE tc_verifications SET board = 'STATE_OTHER' WHERE board = 'STATE_BOARD'");
        } catch (Exception e) {
            log.warn("Could not update tc_verifications for the board master list: {}", e.getMessage());
        }
        int added = 0;
        try (InputStream in = new ClassPathResource("master/admission-age-rules.json").getInputStream()) {
            for (JsonNode n : mapper.readTree(in).get("rules")) {
                String board = n.get("boardCode").asText();
                String cls = n.get("className").asText();
                if (rules.existsByTenantIdIsNullAndBoardCodeAndClassNameIgnoreCase(board, cls)) continue;
                AdmissionAgeRule r = new AdmissionAgeRule();
                r.setBoardCode(board);
                r.setClassName(cls);
                r.setMinAgeYears(n.get("minAgeYears").asInt());
                r.setCutoffMonth(n.get("cutoffMonth").asInt());
                r.setCutoffDay(n.get("cutoffDay").asInt());
                r.setNeedsReview(n.get("needsReview").asBoolean());
                rules.save(r);
                added++;
            }
        }
        log.info("Admission age rule master data: {} added", added);
    }
}
