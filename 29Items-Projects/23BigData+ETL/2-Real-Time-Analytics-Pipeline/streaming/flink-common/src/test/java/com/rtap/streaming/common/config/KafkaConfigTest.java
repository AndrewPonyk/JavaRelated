package com.rtap.streaming.common.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void bootstrapServersPrefersJobParameter() {
        JobParams params = JobParams.of(Map.of("kafka.bootstrap.servers", "param-broker:9092"));
        assertThat(KafkaConfig.bootstrapServers(params)).isEqualTo("param-broker:9092");
    }

    @Test
    void plaintextIsTheDefaultSecurityMode() {
        Properties props = KafkaConfig.clientProperties(JobParams.of(Map.of()));
        assertThat(props).isEmpty();
    }

    @Test
    void mskIamModeConfiguresSaslCallbackChain() {
        Properties props = KafkaConfig.clientProperties(JobParams.of(Map.of("kafka.security", "msk-iam")));

        assertThat(props.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(props.getProperty("sasl.mechanism")).isEqualTo("AWS_MSK_IAM");
        assertThat(props.getProperty("sasl.client.callback.handler.class"))
                .isEqualTo("software.amazon.msk.auth.iam.IAMClientCallbackHandler");
    }

    @Test
    void sinkTransactionTimeoutStaysBelowBrokerCeiling() {
        // Broker transaction.max.timeout.ms is 900000 (MSK config + compose) — TECH-NOTES §3.6.1
        assertThat(Long.parseLong(KafkaConfig.SINK_TRANSACTION_TIMEOUT_MS)).isLessThan(900_000L);
    }
}
