package com.kvs.admission.service;

import com.kvs.admission.service.MeritRankingCalculator.Candidate;
import com.kvs.admission.service.MeritRankingCalculator.RankedCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MeritRankingCalculator} (US-02010).
 */
class MeritRankingCalculatorTest {

    private static final BigDecimal W_ENTRANCE = new BigDecimal("0.7");
    private static final BigDecimal W_INTERVIEW = new BigDecimal("0.3");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private Candidate candidate(String entrance, String interview, int appliedOffsetSeconds) {
        return new Candidate(UUID.randomUUID(),
                entrance != null ? new BigDecimal(entrance) : null,
                interview != null ? new BigDecimal(interview) : null,
                T0.plusSeconds(appliedOffsetSeconds));
    }

    @Test
    @DisplayName("Weighted score combines entrance test and interview, rounded to 2 decimals")
    void weightedScore() {
        assertThat(MeritRankingCalculator.weightedScore(candidate("85.5", "70", 0), W_ENTRANCE, W_INTERVIEW))
                .isEqualByComparingTo("80.85");
        assertThat(MeritRankingCalculator.weightedScore(candidate("66.67", null, 0), BigDecimal.ONE, BigDecimal.ZERO))
                .isEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("Candidates are ordered by weighted score, highest first")
    void ordersByWeightedScore() {
        Candidate low = candidate("50", "50", 0);
        Candidate high = candidate("90", "80", 1);
        Candidate mid = candidate("70", "60", 2);

        List<RankedCandidate> ranked = MeritRankingCalculator.rank(List.of(low, high, mid), W_ENTRANCE, W_INTERVIEW);

        assertThat(ranked).extracting(RankedCandidate::candidate).containsExactly(high, mid, low);
        assertThat(ranked).extracting(RankedCandidate::rank).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Equal weighted score is broken by the higher entrance test score")
    void tieBreakOnEntranceScore() {
        Candidate interviewHeavy = candidate("75", "75", 0); // 52.5 + 22.5 = 75.00
        Candidate entranceHeavy = candidate("78", "68", 1); // 54.6 + 20.4 = 75.00

        List<RankedCandidate> ranked = MeritRankingCalculator.rank(List.of(interviewHeavy, entranceHeavy),
                W_ENTRANCE, W_INTERVIEW);

        assertThat(ranked.get(0).weightedScore()).isEqualByComparingTo(ranked.get(1).weightedScore());
        assertThat(ranked).extracting(RankedCandidate::candidate).containsExactly(entranceHeavy, interviewHeavy);
        assertThat(ranked).extracting(RankedCandidate::rank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Identical scores share a rank and the next rank is skipped (1, 2, 2, 4)")
    void competitionRanking() {
        Candidate first = candidate("95", "90", 0);
        Candidate tieEarly = candidate("80", "70", 1);
        Candidate tieLate = candidate("80", "70", 2);
        Candidate last = candidate("60", "60", 3);

        List<RankedCandidate> ranked = MeritRankingCalculator.rank(List.of(last, tieLate, first, tieEarly),
                W_ENTRANCE, W_INTERVIEW);

        assertThat(ranked).extracting(RankedCandidate::rank).containsExactly(1, 2, 2, 4);
        // ties keep the earlier application first
        assertThat(ranked.get(1).candidate()).isEqualTo(tieEarly);
        assertThat(ranked.get(2).candidate()).isEqualTo(tieLate);
    }

    @Test
    @DisplayName("Empty candidate list yields an empty ranking")
    void emptyList() {
        assertThat(MeritRankingCalculator.rank(List.of(), W_ENTRANCE, W_INTERVIEW)).isEmpty();
    }
}
