package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.config.KafkaConfig;
import com.rtap.streaming.common.model.BusinessEvent;
import com.rtap.streaming.common.model.ConsumedEvent;
import com.rtap.streaming.common.model.DeadLetter;
import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.BusinessEventEnvelopeDeserializer;
import com.rtap.streaming.common.serde.JacksonSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Windowed metric aggregation with end-to-end exactly-once semantics.
 *
 * <pre>
 * events.raw.v1 ──► [envelope deserializer] ──► split ──┬─ dead letters ──► events.raw.dlq.v1
 *                                                       └─ events ─► keyBy(series) ─► 1s windows ─┬─► Kafka (txn) metrics.aggregates.v1
 *                                                                     late ─► events.raw.late.v1  ├─► Elasticsearch (idempotent upsert)
 *                                                                     1m rollup of the 1s stream ─┴─► PostgreSQL (idempotent upsert)
 * </pre>
 *
 * Exactly-once recipe (ARCHITECTURE.md §2.3): checkpointing EXACTLY_ONCE snapshots
 * offsets + window state + sink transactions atomically; the Kafka sink uses
 * transactions (consumers must read_committed); ES/PG flush on the checkpoint barrier
 * with deterministic upsert keys ({@link MetricAggregate#documentId()}).
 *
 * <p>NOTE: on Amazon Managed Flink, checkpoint interval/mode configured here are
 * OVERRIDDEN by the application configuration (Terraform flink-app module) — the
 * values below are local-dev defaults (TECH-NOTES §3.6.6).
 */
public final class MetricsAggregationJob {

    /** Late events (behind the watermark) — routed to a side topic, never silently dropped. */
    static final OutputTag<BusinessEvent> LATE_EVENTS = new OutputTag<BusinessEvent>("late-events") {
    };
    /** Undeserializable/invalid input — routed to the DLQ with a replayable envelope. */
    static final OutputTag<DeadLetter> DEAD_LETTERS = new OutputTag<DeadLetter>("dead-letters") {
    };

    private MetricsAggregationJob() {
    }

    public static void main(String[] args) throws Exception {
        JobParams params = JobParams.from(args);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        buildPipeline(env, params);
        env.execute("rtap-metrics-aggregation");
    }

    /** Assembles the full dataflow on the given environment (separated for tests). */
    public static void buildPipeline(StreamExecutionEnvironment env, JobParams params) {
        final String bootstrap = KafkaConfig.bootstrapServers(params);

        // ── Exactly-once foundations ─────────────────────────────────────────
        env.enableCheckpointing(params.getLong("checkpoint.interval.ms", 10_000L), CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig cp = env.getCheckpointConfig();
        cp.setMinPauseBetweenCheckpoints(params.getLong("checkpoint.min.pause.ms", 2_000L));
        cp.setCheckpointTimeout(120_000L);
        cp.setTolerableCheckpointFailureNumber(3);
        cp.enableUnalignedCheckpoints(); // keeps checkpoints fast under backpressure
        cp.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // State backend (RocksDB, incremental) is set via flink-conf / Managed Flink config, not code.

        // ── Source: raw business events, poison-pill safe ────────────────────
        KafkaSource<ConsumedEvent> source = KafkaSource.<ConsumedEvent>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(KafkaConfig.TOPIC_EVENTS_RAW)
                .setGroupId("rtap-aggregation-v1") // lag monitoring only — Flink assigns partitions itself
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setProperties(KafkaConfig.clientProperties(params))
                .setDeserializer(new BusinessEventEnvelopeDeserializer())
                .build();

        WatermarkStrategy<ConsumedEvent> watermarks = WatermarkStrategy
                .<ConsumedEvent>forBoundedOutOfOrderness(
                        Duration.ofSeconds(params.getLong("watermark.out.of.orderness.s", 5L)))
                .withTimestampAssigner((consumed, ts) -> consumed.timestamp())
                .withIdleness(Duration.ofSeconds(30)); // idle partitions must not stall event time

        SingleOutputStreamOperator<BusinessEvent> events = env
                .fromSource(source, watermarks, "events.raw.v1")
                .uid("src-events-raw")
                .process(new DeadLetterRouter())
                .uid("route-dead-letters")
                .name("split-events-dlq");

        // ── DLQ: observable, replayable, never dropped silently ─────────────
        events.getSideOutput(DEAD_LETTERS)
                .sinkTo(jsonSink(bootstrap, KafkaConfig.TOPIC_EVENTS_DLQ, new JacksonSerializationSchema<DeadLetter>()))
                .uid("sink-dlq")
                .name("kafka-dead-letters");

        // ── 1s tumbling windows — the sub-second hot path ────────────────────
        SingleOutputStreamOperator<MetricAggregate> perSecond = perSecondAggregates(events);

        // ── 1m rollup — re-aggregates the 1s stream (never re-reads raw) ─────
        SingleOutputStreamOperator<MetricAggregate> perMinute = perMinuteRollup(perSecond);

        // ── Sink 1: Kafka, transactional — the correctness backbone ─────────
        // Two DISTINCT sinks: each sink operator derives transactional ids from its
        // prefix, so sharing one prefix across operators makes their producers fence
        // each other (ProducerFencedException crash-loop) — TECH-NOTES §3.6.1 applies
        // within a job, not just across jobs. Caught by AggregationEndToEndIT.
        perSecond.sinkTo(exactlyOnceAggregateSink(bootstrap, params, "1s"))
                .uid("sink-kafka-1s").name("kafka-aggregates-1s");
        perMinute.sinkTo(exactlyOnceAggregateSink(bootstrap, params, "1m"))
                .uid("sink-kafka-1m").name("kafka-aggregates-1m");

        // ── Sink 2: Elasticsearch upsert — powers <1s dashboards ────────────
        if (params.getBoolean("enable-es-sink", false)) {
            perSecond.sinkTo(ElasticsearchSinkFactory.create(params))
                    .uid("sink-es-1s").name("elasticsearch-upsert-1s");
            perMinute.sinkTo(ElasticsearchSinkFactory.create(params))
                    .uid("sink-es-1m").name("elasticsearch-upsert-1m");
        }

        // ── Sink 3: PostgreSQL upsert — durable history ──────────────────────
        if (params.getBoolean("enable-pg-sink", false)) {
            perSecond.sinkTo(PostgresSinkFactory.create(params))
                    .uid("sink-pg-1s").name("postgres-upsert-1s");
            perMinute.sinkTo(PostgresSinkFactory.create(params))
                    .uid("sink-pg-1m").name("postgres-upsert-1m");
        }

        // ── Late data: side output → dedicated topic (alert if ratio > 0.1%) ─
        perSecond.getSideOutput(LATE_EVENTS)
                .sinkTo(jsonSink(bootstrap, KafkaConfig.TOPIC_EVENTS_LATE, new JacksonSerializationSchema<BusinessEvent>()))
                .uid("sink-late-events")
                .name("kafka-late-events");
    }

    /** 1s tumbling event-time windows keyed by metric series (metricKey + dimensions). */
    static SingleOutputStreamOperator<MetricAggregate> perSecondAggregates(DataStream<BusinessEvent> events) {
        return events
                .keyBy(MetricAggregateFunction::seriesKey)
                // Deliberate deferral (ARCHITECTURE §2.4): if one series ever dominates a
                // subtask, salt this key for the 1s stage and merge in the 1m rollup.
                .window(TumblingEventTimeWindows.of(Time.seconds(1)))
                .sideOutputLateData(LATE_EVENTS)
                .aggregate(new MetricAggregateFunction(), new MetricAggregateFunction.ToMetricAggregate("1s"))
                .uid("window-1s")
                .name("aggregate-1s");
    }

    /** 1m rollup cascade over the 1s aggregates. */
    static SingleOutputStreamOperator<MetricAggregate> perMinuteRollup(DataStream<MetricAggregate> perSecond) {
        return perSecond
                .keyBy(agg -> agg.getMetricKey() + '|' + agg.dimensionsHash())
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .reduce(MetricAggregateFunction::merge, new MetricAggregateFunction.RestampWindow("1m"))
                .uid("window-1m")
                .name("rollup-1m");
    }

    /** Transactional sink: exactly-once for read_committed consumers, keyed by metricKey. */
    private static KafkaSink<MetricAggregate> exactlyOnceAggregateSink(String bootstrap, JobParams params,
                                                                       String sinkDiscriminator) {
        return KafkaSink.<MetricAggregate>builder()
                .setBootstrapServers(bootstrap)
                .setKafkaProducerConfig(KafkaConfig.clientProperties(params))
                .setRecordSerializer(KafkaRecordSerializationSchema.<MetricAggregate>builder()
                        .setTopic(KafkaConfig.TOPIC_METRIC_AGGREGATES)
                        .setKeySerializationSchema(
                                (MetricAggregate agg) -> agg.getMetricKey().getBytes(StandardCharsets.UTF_8))
                        .setValueSerializationSchema(new JacksonSerializationSchema<MetricAggregate>())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                // Unique per job AND per sink operator — shared prefixes fence each other.
                .setTransactionalIdPrefix(
                        params.get("kafka.transactional.id.prefix", "rtap-agg") + '-' + sinkDiscriminator)
                // Must stay below broker transaction.max.timeout.ms (TECH-NOTES §3.6.1).
                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, KafkaConfig.SINK_TRANSACTION_TIMEOUT_MS)
                .build();
    }

    /** At-least-once JSON sink for diagnostics streams (DLQ, late events). */
    private static <T> KafkaSink<T> jsonSink(String bootstrap, String topic, JacksonSerializationSchema<T> serializer) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(bootstrap)
                .setRecordSerializer(KafkaRecordSerializationSchema.<T>builder()
                        .setTopic(topic)
                        .setValueSerializationSchema(serializer)
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    /** Splits the consumed stream: valid events flow on, dead letters exit via side output. */
    static final class DeadLetterRouter extends ProcessFunction<ConsumedEvent, BusinessEvent> {

        private static final long serialVersionUID = 1L;

        @Override
        public void processElement(ConsumedEvent consumed, Context ctx, Collector<BusinessEvent> out) {
            if (consumed.isDeadLetter()) {
                ctx.output(DEAD_LETTERS, consumed.getDeadLetter());
            } else {
                out.collect(consumed.getEvent());
            }
        }
    }
}
