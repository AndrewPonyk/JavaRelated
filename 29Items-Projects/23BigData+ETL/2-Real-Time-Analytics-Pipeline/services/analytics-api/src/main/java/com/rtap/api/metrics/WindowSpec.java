package com.rtap.api.metrics;

/**
 * Query windows the API serves. The pipeline materializes two base resolutions
 * (1s hot, 1m rollup); coarser windows are re-bucketed at query time — in SQL via
 * {@code date_bin}, in Elasticsearch via {@code date_histogram fixed_interval} —
 * always from the cheapest sufficient base resolution.
 */
public enum WindowSpec {

    ONE_SECOND("1s", 1_000L, "1s", "1 second"),
    TEN_SECONDS("10s", 10_000L, "1s", "10 seconds"),
    ONE_MINUTE("1m", 60_000L, "1m", "1 minute"),
    FIVE_MINUTES("5m", 300_000L, "1m", "5 minutes"),
    ONE_HOUR("1h", 3_600_000L, "1m", "1 hour");

    private final String label;
    private final long millis;
    private final String baseWindow;   // stored resolution to read (metric_aggregates.window_size / ES index)
    private final String pgInterval;   // date_bin interval literal

    WindowSpec(String label, long millis, String baseWindow, String pgInterval) {
        this.label = label;
        this.millis = millis;
        this.baseWindow = baseWindow;
        this.pgInterval = pgInterval;
    }

    public static WindowSpec parse(String label) {
        for (WindowSpec spec : values()) {
            if (spec.label.equals(label)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("Unsupported window: " + label);
    }

    public String label() { return label; }
    public long millis() { return millis; }
    public String baseWindow() { return baseWindow; }
    public String pgInterval() { return pgInterval; }
    /** Elasticsearch fixed_interval uses the same unit labels. */
    public String esInterval() { return label; }
}
