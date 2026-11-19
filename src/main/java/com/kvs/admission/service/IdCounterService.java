package com.kvs.admission.service;

import com.kvs.admission.entity.IdCounter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Stored counters for SEQUENTIAL ID formats (table id_counters in each school schema): one per school, format key and
 * calendar year. A number is taken with a row-locking UPDATE, so it survives restarts, is never handed out twice (also
 * across service instances) and restarts at 1 each year when the format includes the year. A missing counter is created
 * from the highest number already issued, so starting a counter never repeats an existing number.
 */
@Service
public class IdCounterService {

    /** Counter year of a format that has no year in it: its numbers never restart. */
    public static final int NO_YEAR = 0;

    private static final String ADVANCE =
            "UPDATE id_counters SET last_value = last_value + 1, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE tenant_id = ?1 AND format_key = ?2 AND counter_year = ?3";
    private static final String READ =
            "SELECT last_value FROM id_counters WHERE tenant_id = ?1 AND format_key = ?2 AND counter_year = ?3";
    private static final String CREATE =
            "INSERT INTO id_counters (tenant_id, format_key, counter_year, last_value, updated_at)"
                    + " SELECT ?1, ?2, ?3, ?4, CURRENT_TIMESTAMP WHERE NOT EXISTS"
                    + " (SELECT 1 FROM id_counters WHERE tenant_id = ?1 AND format_key = ?2 AND counter_year = ?3)";

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate newTransaction;

    public IdCounterService(PlatformTransactionManager transactionManager) {
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The next number of a format. The counter row stays locked until the caller's transaction ends, so two requests
     * never get the same number.
     *
     * @param highestIssued highest number already issued for this format and year; asked only when the counter does
     *                      not exist yet (a new school, format or year)
     */
    @Transactional
    public long next(String tenantId, String formatKey, int counterYear, LongSupplier highestIssued) {
        if (advance(tenantId, formatKey, counterYear) == 0) {
            long seed = Math.max(0, highestIssued.getAsLong());
            create(tenantId, formatKey, counterYear, seed);
            if (advance(tenantId, formatKey, counterYear) == 0) {
                throw new IllegalStateException("ID counter " + formatKey + "/" + counterYear + " could not be created");
            }
        }
        Number value = (Number) nativeQuery(READ, tenantId, formatKey, counterYear).getSingleResult();
        return value.longValue();
    }

    /**
     * Highest running number among issued IDs that start with {@code prefix} and continue with digits only
     * (42 for STU-2026-00042 and prefix STU-2026-); 0 when there is none.
     */
    public static long highestNumber(Collection<String> issued, String prefix) {
        long max = 0;
        for (String id : issued) {
            if (id == null || !id.startsWith(prefix)) continue;
            String rest = id.substring(prefix.length());
            if (rest.isEmpty() || rest.length() > 18 || !rest.chars().allMatch(Character::isDigit)) continue;
            max = Math.max(max, Long.parseLong(rest));
        }
        return max;
    }

    /**
     * IDs issued so far that start with {@code prefix}: {@code field} of entity {@code entity}, limited to the school
     * when {@code tenantScoped}. Entity and field names come from the callers' fixed lists, never from requests.
     */
    @Transactional(readOnly = true)
    public List<String> issuedWithPrefix(String entity, String field, boolean tenantScoped, String tenantId, String prefix) {
        String jpql = "select e." + field + " from " + entity + " e where e." + field + " like :prefix escape '!'"
                + (tenantScoped ? " and e.tenantId = :tenantId" : "");
        var query = em.createQuery(jpql, String.class).setParameter("prefix", escapeLike(prefix) + "%");
        if (tenantScoped) query.setParameter("tenantId", tenantId);
        return query.getResultList();
    }

    /** Highest number issued with {@code prefix} in {@code entity.field} (see {@link #issuedWithPrefix}). */
    public long highestIssued(String entity, String field, boolean tenantScoped, String tenantId, String prefix) {
        return highestNumber(issuedWithPrefix(entity, field, tenantScoped, tenantId, prefix), prefix);
    }

    /** Whether {@code value} is already used in {@code entity.field} (of the school when {@code tenantScoped}). */
    @Transactional(readOnly = true)
    public boolean isIssued(String entity, String field, boolean tenantScoped, String tenantId, String value) {
        String jpql = "select count(e) from " + entity + " e where e." + field + " = :value"
                + (tenantScoped ? " and e.tenantId = :tenantId" : "");
        var query = em.createQuery(jpql, Long.class).setParameter("value", value);
        if (tenantScoped) query.setParameter("tenantId", tenantId);
        return query.getSingleResult() > 0;
    }

    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private int advance(String tenantId, String formatKey, int counterYear) {
        return nativeQuery(ADVANCE, tenantId, formatKey, counterYear).executeUpdate();
    }

    /** Creates the counter in its own transaction; one created at the same moment by another request wins. */
    private void create(String tenantId, String formatKey, int counterYear, long seed) {
        try {
            newTransaction.executeWithoutResult(status ->
                    nativeQuery(CREATE, tenantId, formatKey, counterYear).setParameter(4, seed).executeUpdate());
        } catch (DataAccessException | PersistenceException raced) {
            // created by another request first: the caller advances that one
        }
    }

    private Query nativeQuery(String sql, String tenantId, String formatKey, int counterYear) {
        Query query = em.createNativeQuery(sql)
                .setParameter(1, tenantId)
                .setParameter(2, formatKey)
                .setParameter(3, counterYear);
        // Only id_counters is touched: do not flush the caller's pending changes first
        query.unwrap(NativeQuery.class).addSynchronizedEntityClass(IdCounter.class);
        return query;
    }
}
