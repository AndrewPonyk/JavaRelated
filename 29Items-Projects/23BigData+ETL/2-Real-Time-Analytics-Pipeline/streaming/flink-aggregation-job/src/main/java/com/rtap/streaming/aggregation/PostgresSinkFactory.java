package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.model.MetricAggregate;
import org.apache.flink.api.connector.sink2.Sink;

/**
 * Builds the PostgreSQL history sink from job parameters.
 * Enabled with {@code --enable-pg-sink true}; credentials come from job parameters —
 * on AWS these are injected from Secrets Manager into Managed Flink runtime properties.
 */
public final class PostgresSinkFactory {

    private PostgresSinkFactory() {
    }

    public static Sink<MetricAggregate> create(JobParams params) {
        return new PostgresUpsertSink(
                params.get("postgres.url", "jdbc:postgresql://postgres:5432/analytics"),
                params.get("postgres.user", "analytics"),
                params.get("postgres.password", "analytics_local_pw"),
                params.getInt("postgres.batch.size", 500));
    }
}
