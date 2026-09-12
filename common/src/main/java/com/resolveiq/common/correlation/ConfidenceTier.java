package com.resolveiq.common.correlation;

/**
 * Correlation Confidence Tiers strictly conforming to PRD §19, §20.
 * - CONFIRMED_RELATIONSHIP: Composite score >= 0.75
 * - POSSIBLY_RELATED: 0.45 <= Composite score < 0.75
 * - WEAK_RELATIONSHIP: Composite score < 0.45
 */
public enum ConfidenceTier {
    CONFIRMED_RELATIONSHIP(0.75, 1.00),
    POSSIBLY_RELATED(0.45, 0.75),
    WEAK_RELATIONSHIP(0.00, 0.45);

    private final double minScoreInclusive;
    private final double maxScoreExclusive;

    ConfidenceTier(double minScoreInclusive, double maxScoreExclusive) {
        this.minScoreInclusive = minScoreInclusive;
        this.maxScoreExclusive = maxScoreExclusive;
    }

    public double getMinScoreInclusive() {
        return minScoreInclusive;
    }

    public double getMaxScoreExclusive() {
        return maxScoreExclusive;
    }

    public static ConfidenceTier fromScore(double score) {
        if (score >= 0.75) {
            return CONFIRMED_RELATIONSHIP;
        } else if (score >= 0.45) {
            return POSSIBLY_RELATED;
        } else {
            return WEAK_RELATIONSHIP;
        }
    }
}
