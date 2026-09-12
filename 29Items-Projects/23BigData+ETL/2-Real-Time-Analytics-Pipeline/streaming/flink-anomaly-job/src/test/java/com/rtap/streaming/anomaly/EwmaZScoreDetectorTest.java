package com.rtap.streaming.anomaly;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The detector math is pure and unit-tested here; the stateful wiring (keyed state,
 * broadcast models, cooldown, transactional sink) is covered by AnomalyEndToEndIT.
 */
class EwmaZScoreDetectorTest {

    @Test
    void firstObservationSeedsTheBaseline() {
        var s = new EwmaZScoreDetector.DetectorState();

        EwmaZScoreDetector.update(s, 100.0, 0.1);

        assertThat(s.observations).isEqualTo(1);
        assertThat(s.mean).isEqualTo(100.0);
        assertThat(s.variance).isZero();
    }

    @Test
    void constantSeriesIsNeverScoreable() {
        var s = new EwmaZScoreDetector.DetectorState();
        for (int i = 0; i < 50; i++) {
            EwmaZScoreDetector.update(s, 100.0, 0.1);
        }

        assertThat(EwmaZScoreDetector.score(s, 100.0)).isNaN(); // zero variance → no z-score
    }

    @Test
    void spikeAfterNoisyBaselineScoresFarAboveThreshold() {
        var s = new EwmaZScoreDetector.DetectorState();
        // noisy but stable baseline around 100 (deterministic wobble ±3)
        for (int i = 0; i < 60; i++) {
            EwmaZScoreDetector.update(s, 100.0 + (i % 7) - 3, 0.1);
        }

        double zSpike = EwmaZScoreDetector.score(s, 1000.0);
        double zNormal = EwmaZScoreDetector.score(s, 101.0);

        assertThat(zSpike).isGreaterThan(10.0);
        assertThat(Math.abs(zNormal)).isLessThan(3.0);
    }

    @Test
    void dropsScoreNegative() {
        var s = new EwmaZScoreDetector.DetectorState();
        for (int i = 0; i < 60; i++) {
            EwmaZScoreDetector.update(s, 100.0 + (i % 5), 0.1);
        }

        assertThat(EwmaZScoreDetector.score(s, 0.0)).isLessThan(-5.0);
    }

    @Test
    void scoreDoesNotMutateState_scoreThenUpdateContract() {
        var s = new EwmaZScoreDetector.DetectorState();
        for (int i = 0; i < 30; i++) {
            EwmaZScoreDetector.update(s, 50.0 + (i % 3), 0.1);
        }
        double meanBefore = s.mean;
        double varianceBefore = s.variance;

        EwmaZScoreDetector.score(s, 9999.0);

        assertThat(s.mean).isEqualTo(meanBefore);
        assertThat(s.variance).isEqualTo(varianceBefore);
    }

    @Test
    void baselineAdaptsToLevelShifts() {
        var s = new EwmaZScoreDetector.DetectorState();
        for (int i = 0; i < 50; i++) {
            EwmaZScoreDetector.update(s, 100.0, 0.2);
        }
        for (int i = 0; i < 50; i++) {
            EwmaZScoreDetector.update(s, 200.0, 0.2);
        }

        assertThat(s.mean).isGreaterThan(195.0); // EWMA forgot the old level
    }
}
