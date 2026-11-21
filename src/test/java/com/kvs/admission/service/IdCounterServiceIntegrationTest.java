package com.kvs.admission.service;

import com.kvs.admission.entity.IdCounter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The stored ID counters (id_counters) against the test database. */
@SpringBootTest
class IdCounterServiceIntegrationTest {

    @Autowired
    private IdCounterService counters;

    @PersistenceContext
    private EntityManager em;

    private final String tenant = "ZZTEST-" + UUID.randomUUID();

    @Test
    void newCounterStartsAfterTheHighestIssuedNumberAndAsksOnlyOnce() {
        AtomicInteger asked = new AtomicInteger();

        long first = counters.next(tenant, "fmt", 2026, () -> { asked.incrementAndGet(); return 101; });
        long second = counters.next(tenant, "fmt", 2026, () -> { asked.incrementAndGet(); return 500; });

        assertThat(first).isEqualTo(102);
        assertThat(second).isEqualTo(103);
        assertThat(asked).hasValue(1);
    }

    @Test
    void theNumberIsStoredSoItSurvivesARestart() {
        counters.next(tenant, "fmt", 2026, () -> 0);
        counters.next(tenant, "fmt", 2026, () -> 0);

        IdCounter stored = em.find(IdCounter.class, new IdCounter.Key(tenant, "fmt", 2026));
        assertThat(stored.getLastValue()).isEqualTo(2);
    }

    @Test
    void eachSchoolFormatAndYearHasItsOwnCounter() {
        assertThat(counters.next(tenant, "a", 2026, () -> 0)).isEqualTo(1);
        assertThat(counters.next(tenant, "a", 2026, () -> 0)).isEqualTo(2);
        assertThat(counters.next(tenant, "a", 2027, () -> 0)).isEqualTo(1);
        assertThat(counters.next(tenant, "b", 2026, () -> 0)).isEqualTo(1);
        assertThat(counters.next(tenant, "a", IdCounterService.NO_YEAR, () -> 0)).isEqualTo(1);
        assertThat(counters.next(tenant + "-other", "a", 2026, () -> 0)).isEqualTo(1);
    }

    @Test
    void parallelRequestsNeverGetTheSameNumber() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Set<Future<Long>> futures = new HashSet<>();
            for (int i = 0; i < 40; i++) {
                futures.add(pool.submit(() -> counters.next(tenant, "par", 2026, () -> 0)));
            }
            Set<Long> numbers = new HashSet<>();
            for (Future<Long> f : futures) numbers.add(f.get());
            assertThat(numbers).hasSize(40).contains(1L, 40L);
        } finally {
            pool.shutdown();
        }
    }
}
