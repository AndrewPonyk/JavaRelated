package com.rtap.streaming.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobParamsTest {

    @Test
    void parsesKeyValueArgsAndBareFlags() {
        JobParams params = JobParams.from(new String[]{
                "--kafka.bootstrap.servers", "broker:9092",
                "--enable-es-sink",
                "--checkpoint.interval.ms", "500"});

        assertThat(params.get("kafka.bootstrap.servers", "x")).isEqualTo("broker:9092");
        assertThat(params.getBoolean("enable-es-sink", false)).isTrue();
        assertThat(params.getLong("checkpoint.interval.ms", 0)).isEqualTo(500L);
    }

    @Test
    void typedGettersFallBackToDefaults() {
        JobParams params = JobParams.from(new String[0]);

        assertThat(params.get("missing", "fallback")).isEqualTo("fallback");
        assertThat(params.getBoolean("missing", true)).isTrue();
        assertThat(params.getInt("missing", 7)).isEqualTo(7);
        assertThat(params.getDouble("missing", 1.5)).isEqualTo(1.5);
    }

    @Test
    void requireFailsLoudlyOnMissingParameter() {
        assertThatThrownBy(() -> JobParams.from(new String[0]).require("postgres.url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--postgres.url");
    }

    @Test
    void ofBuildsFromMapForTests() {
        JobParams params = JobParams.of(java.util.Map.of("detector.alpha", "0.2"));
        assertThat(params.getDouble("detector.alpha", 0.05)).isEqualTo(0.2);
    }

    @Test
    void nonNumericValuesFailFastWithTheParameterName() {
        JobParams params = JobParams.of(java.util.Map.of(
                "checkpoint.interval.ms", "ten",
                "detector.warmup.windows", "many",
                "detector.alpha", "tiny"));

        assertThatThrownBy(() -> params.getLong("checkpoint.interval.ms", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--checkpoint.interval.ms").hasMessageContaining("'ten'");
        assertThatThrownBy(() -> params.getInt("detector.warmup.windows", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--detector.warmup.windows");
        assertThatThrownBy(() -> params.getDouble("detector.alpha", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--detector.alpha");
    }
}
