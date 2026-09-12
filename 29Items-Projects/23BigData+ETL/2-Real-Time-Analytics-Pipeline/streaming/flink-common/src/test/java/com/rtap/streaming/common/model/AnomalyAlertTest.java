package com.rtap.streaming.common.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyAlertTest {

    @Test
    void severityLadderScalesWithThresholdMultiples() {
        assertThat(AnomalyAlert.severityFor(4.5, 4.0)).isEqualTo(AnomalyAlert.SEVERITY_WARNING);
        assertThat(AnomalyAlert.severityFor(6.0, 4.0)).isEqualTo(AnomalyAlert.SEVERITY_SERIOUS);
        assertThat(AnomalyAlert.severityFor(8.0, 4.0)).isEqualTo(AnomalyAlert.SEVERITY_CRITICAL);
        assertThat(AnomalyAlert.severityFor(-9.1, 4.0)).isEqualTo(AnomalyAlert.SEVERITY_CRITICAL); // drops too
    }

    @Test
    void factoryCarriesTheTriggeringWindowAndDimensions() {
        MetricAggregate agg = new MetricAggregate();
        agg.setMetricKey("payments.captured");
        agg.setWindowSize("1s");
        agg.setWindowStart(1_700_000_000_000L);
        agg.setWindowEnd(1_700_000_001_000L);
        agg.setDimensions(Map.of("region", "eu"));

        AnomalyAlert alert = AnomalyAlert.of(agg, "ewma-zscore", 6.2, 4.0, 990.0, 120.0);

        assertThat(alert.getAlertId()).isNotBlank();
        assertThat(alert.getMetricKey()).isEqualTo("payments.captured");
        assertThat(alert.getSeverity()).isEqualTo(AnomalyAlert.SEVERITY_SERIOUS);
        assertThat(alert.getObserved()).isEqualTo(990.0);
        assertThat(alert.getExpected()).isEqualTo(120.0);
        assertThat(alert.getWindowStart()).isEqualTo(1_700_000_000_000L);
        assertThat(alert.getDimensions()).containsEntry("region", "eu");
        assertThat(alert.getDetectedAt()).isPositive();
    }
}
