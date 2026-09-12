from processor.anomaly.detector import EwmaAnomalyDetector


def test_stable_series_never_alerts():
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.0, warmup=20)

    results = [detector.score("orders_per_second", 100.0 + (i % 3)) for i in range(200)]

    assert not any(r.is_anomaly for r in results)


def test_spike_after_warmup_is_flagged():
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.0, warmup=20)
    for i in range(100):
        detector.score("orders_per_second", 100.0 + (i % 3))

    result = detector.score("orders_per_second", 500.0)

    assert result.is_anomaly
    assert result.score >= 4.0


def test_anomalies_do_not_poison_the_baseline():
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.0, warmup=20)
    for i in range(100):
        detector.score("m", 100.0 + (i % 3))
    baseline_before = detector.score("m", 500.0).baseline_mean

    baseline_after = detector.score("m", 100.0).baseline_mean

    # The 500.0 spike must not have shifted the mean toward it.
    assert abs(baseline_after - baseline_before) < 1.0


def test_metrics_are_isolated():
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.0, warmup=20)
    for i in range(100):
        detector.score("warm_metric", 100.0 + (i % 3))

    # A brand-new metric starts its own warmup — extreme first values don't alert.
    first = detector.score("cold_metric", 99999.0)
    second = detector.score("cold_metric", 1.0)

    assert not first.is_anomaly
    assert not second.is_anomaly
