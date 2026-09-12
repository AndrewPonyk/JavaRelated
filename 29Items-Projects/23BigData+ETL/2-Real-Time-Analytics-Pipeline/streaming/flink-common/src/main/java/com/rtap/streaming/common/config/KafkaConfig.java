package com.rtap.streaming.common.config;

import java.util.Properties;

/**
 * Single source of truth for topic names and Kafka client settings inside the jobs.
 * Topic definitions (partitions/retention/configs) live in {@code kafka/topics.yaml};
 * the constants here MUST stay in sync with that file.
 */
public final class KafkaConfig {

    // ── Topics ────────────────────────────────────────────────────────────────
    public static final String TOPIC_EVENTS_RAW = "events.raw.v1";
    public static final String TOPIC_EVENTS_DLQ = "events.raw.dlq.v1";
    public static final String TOPIC_EVENTS_LATE = "events.raw.late.v1";
    public static final String TOPIC_METRIC_AGGREGATES = "metrics.aggregates.v1";
    public static final String TOPIC_ANOMALY_ALERTS = "alerts.anomalies.v1";
    public static final String TOPIC_MODEL_UPDATES = "ml.model-updates.v1"; // compacted

    /**
     * Broker default {@code transaction.max.timeout.ms} is 15 min; the sink's producer
     * transaction timeout must stay BELOW it or the first checkpoint dies with
     * InvalidTxnTimeoutException (TECH-NOTES §3.6.1).
     */
    public static final String SINK_TRANSACTION_TIMEOUT_MS = "600000"; // 10 min

    private KafkaConfig() {
    }

    /**
     * Bootstrap servers resolution: {@code --kafka.bootstrap.servers} job arg /
     * Managed Flink runtime property → {@code KAFKA_BOOTSTRAP_SERVERS} env →
     * compose-internal default.
     */
    public static String bootstrapServers(JobParams params) {
        String env = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        return params.get("kafka.bootstrap.servers", env != null ? env : "kafka:9092");
    }

    /**
     * Client security properties. {@code --kafka.security msk-iam} enables AWS MSK IAM
     * SASL auth (port 9098; the aws-msk-iam-auth callback handler is shaded into every
     * job jar). Default is plaintext for the local compose broker.
     */
    public static Properties clientProperties(JobParams params) {
        Properties props = new Properties();
        if ("msk-iam".equalsIgnoreCase(params.get("kafka.security", "plaintext"))) {
            props.setProperty("security.protocol", "SASL_SSL");
            props.setProperty("sasl.mechanism", "AWS_MSK_IAM");
            props.setProperty("sasl.jaas.config",
                    "software.amazon.msk.auth.iam.IAMLoginModule required;");
            props.setProperty("sasl.client.callback.handler.class",
                    "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        }
        return props;
    }
}
