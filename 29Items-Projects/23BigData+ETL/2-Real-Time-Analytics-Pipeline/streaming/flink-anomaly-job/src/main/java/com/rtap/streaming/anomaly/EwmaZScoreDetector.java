package com.rtap.streaming.anomaly;

import com.rtap.streaming.common.model.AnomalyAlert;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.model.ModelParams;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Online anomaly detector: exponentially-weighted moving average (EWMA) of mean and
 * variance per metric series, corrected by offline-trained seasonal baselines that
 * arrive via broadcast state (ADR #6) — alerting when the observed window value
 * deviates by more than {@code zThreshold} standard deviations from expectation.
 *
 * <p>Scoring pipeline per element:
 * <ol>
 *   <li><b>Deseasonalize:</b> {@code residual = observed − seasonalBaseline(t)} using
 *       the broadcast {@link ModelParams} for the metric (0 when no model — graceful
 *       degradation to pure online EWMA);</li>
 *   <li><b>Score-then-update:</b> the residual is scored against the CURRENT baseline
 *       before it contaminates the baseline;</li>
 *   <li><b>Gate:</b> warm-up windows before the first alert; per-series cooldown
 *       suppresses alert storms during a sustained anomaly.</li>
 * </ol>
 *
 * <p>State is O(1) per series (three numbers + timestamps) with a 24h TTL — series
 * that stop emitting are evicted; unbounded key cardinality is otherwise a slow
 * memory leak (TECH-NOTES §3.6.8).
 */
public class EwmaZScoreDetector
        extends KeyedBroadcastProcessFunction<String, MetricAggregate, ModelParams, AnomalyAlert> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EwmaZScoreDetector.class);

    static final String DETECTOR_ONLINE = "ewma-zscore";
    static final String DETECTOR_SEASONAL = "seasonal-baseline";
    static final double MIN_STD = 1e-9; // constant series → never scoreable

    /** Broadcast state: latest model per metricKey (compacted control topic). */
    public static final MapStateDescriptor<String, ModelParams> MODELS_DESCRIPTOR =
            new MapStateDescriptor<>("anomaly-models", String.class, ModelParams.class);

    private final double alpha;
    private final double defaultZThreshold;
    private final int warmupWindows;
    private final long cooldownMs;

    private transient ValueState<DetectorState> state;

    public EwmaZScoreDetector(double alpha, double defaultZThreshold, int warmupWindows, long cooldownMs) {
        this.alpha = alpha;
        this.defaultZThreshold = defaultZThreshold;
        this.warmupWindows = warmupWindows;
        this.cooldownMs = cooldownMs;
    }

    /** Per-series state — a Flink POJO. */
    public static class DetectorState implements Serializable {
        private static final long serialVersionUID = 1L;

        public long observations;
        public double mean;
        public double variance;
        public long lastAlertAtMs;
    }

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<DetectorState> descriptor =
                new ValueStateDescriptor<>("ewma-state", DetectorState.class);
        descriptor.enableTimeToLive(StateTtlConfig
                .newBuilder(Time.hours(24))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build());
        state = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processBroadcastElement(ModelParams model, Context ctx, Collector<AnomalyAlert> out) throws Exception {
        if (model == null || !model.isValid()) {
            LOG.warn("Ignoring invalid model update: {}", model);
            return;
        }
        ctx.getBroadcastState(MODELS_DESCRIPTOR).put(model.getMetricKey(), model);
        LOG.info("Anomaly model updated: {} → version {}", model.getMetricKey(), model.getModelVersion());
    }

    @Override
    public void processElement(MetricAggregate agg, ReadOnlyContext ctx, Collector<AnomalyAlert> out) throws Exception {
        // Monitored signal: per-window SUM (volume/throughput shape). Latency-style
        // metrics would monitor avg — selectable per metric once definitions carry it.
        final double observed = agg.getSum();
        final ModelParams model = ctx.getBroadcastState(MODELS_DESCRIPTOR).get(agg.getMetricKey());

        final double seasonal = model != null ? model.expectedAt(agg.getWindowStart()) : 0.0;
        final double residual = observed - seasonal;
        final double threshold = model != null && model.getZThreshold() > 0
                ? model.getZThreshold() : defaultZThreshold;

        DetectorState s = state.value();
        if (s == null) {
            s = new DetectorState();
        }

        // 1) Score the residual against the CURRENT baseline (before updating it).
        double z = score(s, residual);
        if (!Double.isNaN(z) && Math.abs(z) > threshold && s.observations >= warmupWindows) {
            long now = ctx.timerService().currentProcessingTime();
            if (now - s.lastAlertAtMs >= cooldownMs) {
                s.lastAlertAtMs = now;
                AnomalyAlert alert = AnomalyAlert.of(agg,
                        model != null ? DETECTOR_SEASONAL : DETECTOR_ONLINE,
                        z, threshold, observed, s.mean + seasonal);
                alert.setModelVersion(model != null ? model.getModelVersion() : "-");
                out.collect(alert);
            }
        }

        // 2) Fold the observation into the adaptive baseline.
        update(s, residual, alpha);
        state.update(s);
    }

    /** z-score of x against the state's EWMA baseline; NaN while unscoreable. */
    static double score(DetectorState s, double x) {
        double std = Math.sqrt(s.variance);
        if (s.observations == 0 || std <= MIN_STD) {
            return Double.NaN;
        }
        return (x - s.mean) / std;
    }

    /** EWMA mean/variance update (West 1979). */
    static void update(DetectorState s, double x, double alpha) {
        if (s.observations == 0) {
            s.mean = x;
            s.variance = 0.0;
        } else {
            double delta = x - s.mean;
            s.mean += alpha * delta;
            s.variance = (1.0 - alpha) * (s.variance + alpha * delta * delta);
        }
        s.observations++;
    }
}
