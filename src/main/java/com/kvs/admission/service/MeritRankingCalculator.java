package com.kvs.admission.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure ranking logic for merit lists (US-02010), kept free of persistence for unit testing.
 * <p>
 * Weighted score = entranceTest x entranceWeight + interview x interviewWeight (scores are percentages).
 * Order: weighted score desc, then entrance test score desc, then earliest application first.
 * Candidates equal on both weighted and entrance test score share a rank (standard competition
 * ranking: 1, 2, 2, 4).
 */
public final class MeritRankingCalculator {

    private MeritRankingCalculator() {
    }

    public record Candidate(UUID enquiryId, BigDecimal entranceTestScore, BigDecimal interviewScore,
                            Instant appliedAt) {
    }

    public record RankedCandidate(Candidate candidate, BigDecimal weightedScore, int rank) {
    }

    public static BigDecimal weightedScore(Candidate c, BigDecimal entranceWeight, BigDecimal interviewWeight) {
        BigDecimal entrance = c.entranceTestScore() != null ? c.entranceTestScore() : BigDecimal.ZERO;
        BigDecimal interview = c.interviewScore() != null ? c.interviewScore() : BigDecimal.ZERO;
        return entrance.multiply(entranceWeight)
                .add(interview.multiply(interviewWeight))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static List<RankedCandidate> rank(List<Candidate> candidates, BigDecimal entranceWeight,
                                             BigDecimal interviewWeight) {
        List<RankedCandidate> scored = new ArrayList<>(candidates.stream()
                .map(c -> new RankedCandidate(c, weightedScore(c, entranceWeight, interviewWeight), 0))
                .toList());

        Comparator<RankedCandidate> order = Comparator
                .comparing(RankedCandidate::weightedScore, Comparator.reverseOrder())
                .thenComparing(r -> entranceOrZero(r.candidate()), Comparator.reverseOrder())
                .thenComparing(r -> r.candidate().appliedAt(), Comparator.nullsLast(Comparator.naturalOrder()));
        scored.sort(order);

        List<RankedCandidate> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            RankedCandidate current = scored.get(i);
            int rank = i + 1;
            if (i > 0) {
                RankedCandidate previous = ranked.get(i - 1);
                if (previous.weightedScore().compareTo(current.weightedScore()) == 0
                        && entranceOrZero(previous.candidate()).compareTo(entranceOrZero(current.candidate())) == 0) {
                    rank = previous.rank();
                }
            }
            ranked.add(new RankedCandidate(current.candidate(), current.weightedScore(), rank));
        }
        return ranked;
    }

    private static BigDecimal entranceOrZero(Candidate c) {
        return c.entranceTestScore() != null ? c.entranceTestScore() : BigDecimal.ZERO;
    }
}
