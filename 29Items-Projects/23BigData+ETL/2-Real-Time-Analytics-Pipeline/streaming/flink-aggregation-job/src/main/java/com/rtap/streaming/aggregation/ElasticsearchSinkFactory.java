package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.config.JobParams;
import com.rtap.streaming.common.model.MetricAggregate;
import org.apache.flink.api.connector.sink2.Sink;

/**
 * Builds the Elasticsearch hot-path sink from job parameters.
 * Enabled with {@code --enable-es-sink true}; local default URL targets the compose
 * network, AWS passes the OpenSearch endpoint via Managed Flink runtime properties.
 */
public final class ElasticsearchSinkFactory {

    private ElasticsearchSinkFactory() {
    }

    public static Sink<MetricAggregate> create(JobParams params) {
        return new ElasticsearchBulkSink(
                params.get("elasticsearch.url", "http://elasticsearch:9200"),
                params.get("elasticsearch.index.prefix", "metrics-aggregates"),
                params.get("elasticsearch.username", ""),
                params.get("elasticsearch.password", ""),
                params.getInt("elasticsearch.bulk.max-actions", 1000),
                params.getInt("elasticsearch.bulk.max-retries", 5));
    }
}
