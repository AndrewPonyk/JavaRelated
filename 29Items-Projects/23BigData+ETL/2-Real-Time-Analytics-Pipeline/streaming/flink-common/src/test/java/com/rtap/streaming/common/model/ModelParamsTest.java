package com.rtap.streaming.common.model;

import com.rtap.streaming.common.serde.Json;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelParamsTest {

    /** 2024-01-01 is a Monday; bucket 0 is Monday 00:00 UTC. */
    private static final long MONDAY_MIDNIGHT_UTC = 1_704_067_200_000L;

    @Test
    void hourOfWeekBucketsMondayFirst() {
        assertThat(ModelParams.hourOfWeek(MONDAY_MIDNIGHT_UTC)).isZero();
        assertThat(ModelParams.hourOfWeek(MONDAY_MIDNIGHT_UTC + 3_600_000L)).isEqualTo(1);   // Mon 01:00
        assertThat(ModelParams.hourOfWeek(MONDAY_MIDNIGHT_UTC + 24 * 3_600_000L)).isEqualTo(24); // Tue 00:00
        assertThat(ModelParams.hourOfWeek(MONDAY_MIDNIGHT_UTC - 3_600_000L)).isEqualTo(167); // Sun 23:00
    }

    @Test
    void expectedAtReadsTheSeasonalBucket() {
        double[] means = new double[ModelParams.HOURS_PER_WEEK];
        means[0] = 100.0;
        means[25] = 55.5; // Tuesday 01:00
        ModelParams model = new ModelParams();
        model.setMetricKey("orders.completed");
        model.setSeasonalMeans(means);

        assertThat(model.expectedAt(MONDAY_MIDNIGHT_UTC)).isEqualTo(100.0);
        assertThat(model.expectedAt(MONDAY_MIDNIGHT_UTC + 25 * 3_600_000L)).isEqualTo(55.5);
    }

    @Test
    void modelWithoutSeasonalityIsValidAndExpectsZero() {
        ModelParams model = new ModelParams();
        model.setMetricKey("orders.completed");

        assertThat(model.isValid()).isTrue();
        assertThat(model.expectedAt(MONDAY_MIDNIGHT_UTC)).isZero();
    }

    @Test
    void rejectsPartialSeasonalArraysAndMissingKey() {
        ModelParams truncated = new ModelParams();
        truncated.setMetricKey("m");
        truncated.setSeasonalMeans(new double[10]);
        assertThat(truncated.isValid()).isFalse();

        ModelParams keyless = new ModelParams();
        assertThat(keyless.isValid()).isFalse();
    }

    @Test
    void deserializesTheTrainerContract() throws Exception {
        String json = """
                {"metricKey":"orders.completed","modelVersion":"2026-07-03T02:00Z",
                 "zThreshold":4.0,"seasonalMeans":%s,"seasonalStds":%s}
                """.formatted(java.util.Arrays.toString(new double[168]),
                java.util.Arrays.toString(new double[168]));

        ModelParams model = Json.MAPPER.readValue(json, ModelParams.class);

        assertThat(model.isValid()).isTrue();
        assertThat(model.getModelVersion()).isEqualTo("2026-07-03T02:00Z");
        assertThat(model.getZThreshold()).isEqualTo(4.0);
        assertThat(model.getSeasonalMeans()).hasSize(168);
    }
}
