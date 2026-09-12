package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.AggregatePoint;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Historical aggregate queries against PostgreSQL (the durable store).
 *
 * <p>Two things happen in one query: dimension series are merged
 * (SUM/MIN/MAX group-by — the pipeline stores one row per dimension combination),
 * and rows are re-bucketed into the requested window via {@code date_bin} when it is
 * coarser than the stored base resolution.
 */
@Repository
public class MetricAggregateRepository {

    private static final RowMapper<AggregatePoint> MAPPER = (rs, rowNum) -> {
        long count = rs.getLong("event_count");
        double sum = rs.getDouble("value_sum");
        return new AggregatePoint(
                rs.getTimestamp("bucket_start").toInstant(),
                count,
                sum,
                rs.getDouble("value_min"),
                rs.getDouble("value_max"),
                count == 0 ? 0.0 : sum / count);
    };

    private final JdbcClient jdbc;

    public MetricAggregateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AggregatePoint> query(String metricKey, WindowSpec window, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT date_bin(CAST(:bucket AS interval), window_start, TIMESTAMPTZ 'epoch') AS bucket_start,
                               SUM(event_count) AS event_count,
                               SUM(value_sum)   AS value_sum,
                               MIN(value_min)   AS value_min,
                               MAX(value_max)   AS value_max
                        FROM metric_aggregates
                        WHERE metric_key = :metricKey
                          AND window_size = :baseWindow
                          AND window_start >= :fromTs AND window_start < :toTs
                        GROUP BY bucket_start
                        ORDER BY bucket_start
                        """)
                .param("bucket", window.pgInterval())
                .param("metricKey", metricKey)
                .param("baseWindow", window.baseWindow())
                .param("fromTs", Timestamp.from(from))
                .param("toTs", Timestamp.from(to))
                .query(MAPPER)
                .list();
    }
}
