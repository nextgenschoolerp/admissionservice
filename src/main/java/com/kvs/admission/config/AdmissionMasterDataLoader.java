package com.kvs.admission.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.admission.entity.AdmissionAgeRule;
import com.kvs.admission.repo.AdmissionAgeRuleRepository;
import com.kvs.core.tenancy.TenantJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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
    private final TenantJobs tenantJobs;
    private final TransactionTemplate tx;

    public AdmissionMasterDataLoader(AdmissionAgeRuleRepository rules, ObjectMapper mapper, JdbcTemplate jdbc,
                                     TenantJobs tenantJobs, PlatformTransactionManager transactionManager) {
        this.rules = rules;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.tenantJobs = tenantJobs;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // tc_verifications is school data: fix it in each school's schema (once, for all, when schema routing is off).
        // Outside any transaction, so each school's statements get a connection pointed at that school.
        tenantJobs.forEachSchool("tc_verifications board update", tenantId -> {
            try {
                jdbc.execute("ALTER TABLE IF EXISTS tc_verifications DROP CONSTRAINT IF EXISTS tc_verifications_board_check");
                jdbc.update("UPDATE tc_verifications SET board = 'STATE_OTHER' WHERE board = 'STATE_BOARD'");
            } catch (Exception e) {
                log.warn("Could not update tc_verifications for the board master list{}: {}",
                        tenantId == null ? "" : " (school " + tenantId + ")", e.getMessage());
            }
        });
        // the master age rules (tenant_id IS NULL) are shared: with routing on they go to the common schema
        tx.executeWithoutResult(status -> loadMasterRules());
    }

    private void loadMasterRules() {
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
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read master/admission-age-rules.json", e);
        }
        log.info("Admission age rule master data: {} added", added);
    }
}
