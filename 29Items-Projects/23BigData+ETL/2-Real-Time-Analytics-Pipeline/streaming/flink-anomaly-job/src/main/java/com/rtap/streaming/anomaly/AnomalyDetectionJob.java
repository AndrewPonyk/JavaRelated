package com.rtap.streaming.anomaly;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.config.KafkaConfig;
import com.rtap.streaming.common.model.AnomalyAlert;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.model.ModelParams;
import com.rtap.streaming.common.serde.JacksonDeserializationSchema;
import com.rtap.streaming.common.serde.JacksonSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.nio.charset.StandardCharsets;

/**
 * ML anomaly detection on the streaming aggregates.
 *
 * <pre>
 * metrics.aggregates.v1 (read_committed!) ──► keyBy(metric series) ──► EWMA z-score
 *                                                          │                │ anomalous
 * ml.model-updates.v1 (broadcast state) ───────────────────┘                ▼
 *                                                              alerts.anomalies.v1 (transactional)
 * </pre>
 *
 * Detection strategy (ADR #6): a fully online EWMA detector alerts within one window
 * of a shift with zero external dependencies; offline-trained seasonal baselines
 * arrive via the compacted control topic and correct for daily/weekly cycles —
 * models update WITHOUT a job redeploy.
 */
public final class AnomalyDetectionJob {

    private AnomalyDetectionJob() {
    }

    public static void main(String[] args) throws Exception {
        JobParams params = JobParams.from(args);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        buildPipeline(env, params);
        env.execute("rtap-anomaly-detection");
    }

    /** Assembles the full dataflow on the given environment (separated for tests). */
    public static void buildPipeline(StreamExecutionEnvironment env, JobParams params) {
        final String bootstrap = KafkaConfig.bootstrapServers(params);
        env.enableCheckpointing(params.getLong("checkpoint.interval.ms", 10_000L), CheckpointingMode.EXACTLY_ONCE);

        // ── Source 1: committed aggregates only ──────────────────────────────
        // read_committed is NON-NEGOTIABLE: the upstream sink is transactional, and
        // reading uncommitted data would score aborted duplicates (TECH-NOTES §3.6.2).
        // First deploy starts from EARLIEST — replaying recent aggregates warms the
        // detectors so alerting is meaningful sooner.
        KafkaSource<MetricAggregate> aggregatesSource = KafkaSource.<MetricAggregate>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(KafkaConfig.TOPIC_METRIC_AGGREGATES)
                .setGroupId("rtap-anomaly-v1")
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
                .setProperties(KafkaConfig.clientProperties(params))
                .setValueOnlyDeserializer(new JacksonDeserializationSchema<>(MetricAggregate.class))
                .build();

        WatermarkStrategy<MetricAggregate> watermarks = WatermarkStrategy
                .<MetricAggregate>forBoundedOutOfOrderness(java.time.Duration.ofSeconds(2))
                .withTimestampAssigner((agg, ts) -> agg.getWindowEnd())
                .withIdleness(java.time.Duration.ofSeconds(30));

        DataStream<MetricAggregate> aggregates = env
                .fromSource(aggregatesSource, watermarks, "metrics.aggregates.v1")
                .uid("src-aggregates")
                .filter(new HotResolutionOnly()) // drops serde nulls + non-1s resolutions
                .uid("filter-1s-resolution");

        // ── Source 2: model updates (compacted → EARLIEST rebuilds full state) ─
        KafkaSource<ModelParams> modelSource = KafkaSource.<ModelParams>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(KafkaConfig.TOPIC_MODEL_UPDATES)
                .setGroupId("rtap-anomaly-models-v1")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setProperties(KafkaConfig.clientProperties(params))
                .setValueOnlyDeserializer(new JacksonDeserializationSchema<>(ModelParams.class))
                .build();

        BroadcastStream<ModelParams> models = env
                .fromSource(modelSource, WatermarkStrategy.noWatermarks(), "ml.model-updates.v1")
                .uid("src-model-updates")
                .filter(new DropNulls<ModelParams>())
                .uid("filter-model-poison-pills")
                .broadcast(EwmaZScoreDetector.MODELS_DESCRIPTOR);

        // ── Detection: keyed detector state × broadcast model state ─────────
        DataStream<AnomalyAlert> alerts = aggregates
                .keyBy(agg -> agg.getMetricKey() + '|' + agg.dimensionsHash()) // one detector per series
                .connect(models)
                .process(new EwmaZScoreDetector(
                        params.getDouble("detector.alpha", 0.05),
                        params.getDouble("detector.z.threshold", 4.0),
                        params.getInt("detector.warmup.windows", 30),
                        params.getLong("detector.cooldown.ms", 30_000L)))
                .uid("ewma-zscore-detector")
                .name("anomaly-detector");

        // ── Sink: transactional — alert consumers see each alert exactly once ─
        alerts.sinkTo(KafkaSink.<AnomalyAlert>builder()
                        .setBootstrapServers(bootstrap)
                        .setKafkaProducerConfig(KafkaConfig.clientProperties(params))
                        .setRecordSerializer(KafkaRecordSerializationSchema.<AnomalyAlert>builder()
                                .setTopic(KafkaConfig.TOPIC_ANOMALY_ALERTS)
                                .setKeySerializationSchema(
                                        (AnomalyAlert a) -> a.getMetricKey().getBytes(StandardCharsets.UTF_8))
                                .setValueSerializationSchema(new JacksonSerializationSchema<AnomalyAlert>())
                                .build())
                        .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                        .setTransactionalIdPrefix(params.get("kafka.transactional.id.prefix", "rtap-anomaly"))
                        .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, KafkaConfig.SINK_TRANSACTION_TIMEOUT_MS)
                        .build())
                .uid("sink-alerts")
                .name("kafka-anomaly-alerts");
    }

    /**
     * Named, explicitly-typed filters instead of shared-shape lambdas/method refs:
     * with two sources in one job, `Objects::nonNull` at multiple call sites left the
     * graph wiring ambiguous enough to chain a MetricAggregate-typed filter onto the
     * ModelParams source (ClassCastException at runtime — caught by AnomalyEndToEndIT).
     */
    static final class DropNulls<T> implements FilterFunction<T> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean filter(T value) {
            return value != null;
        }
    }

    static final class HotResolutionOnly implements FilterFunction<MetricAggregate> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean filter(MetricAggregate agg) {
            return agg != null && "1s".equals(agg.getWindowSize());
        }
    }
}
