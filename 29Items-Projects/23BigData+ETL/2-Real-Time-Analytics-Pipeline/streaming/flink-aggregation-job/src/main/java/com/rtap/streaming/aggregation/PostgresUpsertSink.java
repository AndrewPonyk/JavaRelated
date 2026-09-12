package com.rtap.streaming.aggregation;

import com.rtap.streaming.common.model.MetricAggregate;
import com.rtap.streaming.common.serde.Json;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL upsert sink — durable aggregate history.
 *
 * <p><b>Deliberately NOT XA/two-phase</b> (TECH-NOTES §3.6.10): the batch is flushed
 * and committed on every checkpoint barrier (at-least-once), and the statement is an
 * idempotent {@code INSERT … ON CONFLICT DO UPDATE} on the natural key — identical to
 * the Elasticsearch {@code _id} — so replays overwrite instead of duplicating:
 * effectively-once, with none of the prepared-transaction fragility.
 *
 * <p>Failure policy: a failed flush rolls back and throws — the checkpoint fails, the
 * job restarts from the last consistent state and replays into the upsert.
 */
public class PostgresUpsertSink implements Sink<MetricAggregate>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PostgresUpsertSink.class);

    /** Natural key matches migrations/V001__core_schema.sql PK — the idempotence contract. */
    static final String UPSERT_SQL = """
            INSERT INTO metric_aggregates
              (metric_key, window_size, window_start, window_end, dimensions_hash, dimensions,
               event_count, value_sum, value_min, value_max)
            VALUES (?, ?, to_timestamp(? / 1000.0), to_timestamp(? / 1000.0), ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (metric_key, window_size, window_start, dimensions_hash)
            DO UPDATE SET
              window_end  = EXCLUDED.window_end,
              dimensions  = EXCLUDED.dimensions,
              event_count = EXCLUDED.event_count,
              value_sum   = EXCLUDED.value_sum,
              value_min   = EXCLUDED.value_min,
              value_max   = EXCLUDED.value_max,
              updated_at  = now()
            """;

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final int batchSize;

    public PostgresUpsertSink(String jdbcUrl, String user, String password, int batchSize) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.batchSize = batchSize;
    }

    @Override
    public SinkWriter<MetricAggregate> createWriter(InitContext context) throws IOException {
        return newWriter();
    }

    /** Visible for tests. */
    UpsertWriter newWriter() throws IOException {
        return new UpsertWriter();
    }

    class UpsertWriter implements SinkWriter<MetricAggregate> {

        private final Connection connection;
        private final PreparedStatement statement;
        private int pending;

        UpsertWriter() throws IOException {
            Connection candidate = null;
            try {
                candidate = DriverManager.getConnection(jdbcUrl, user, password);
                candidate.setAutoCommit(false);
                statement = candidate.prepareStatement(UPSERT_SQL);
                connection = candidate;
            } catch (SQLException e) {
                if (candidate != null) {
                    try {
                        candidate.close(); // don't leak the connection when setup fails halfway
                    } catch (SQLException closeFailure) {
                        e.addSuppressed(closeFailure);
                    }
                }
                throw new IOException("Cannot open PostgreSQL connection to " + jdbcUrl, e);
            }
        }

        @Override
        public void write(MetricAggregate agg, Context context) throws IOException {
            try {
                statement.setString(1, agg.getMetricKey());
                statement.setString(2, agg.getWindowSize());
                statement.setLong(3, agg.getWindowStart());
                statement.setLong(4, agg.getWindowEnd());
                statement.setString(5, agg.dimensionsHash());
                statement.setString(6, Json.MAPPER.writeValueAsString(agg.getDimensions()));
                statement.setLong(7, agg.getCount());
                statement.setDouble(8, agg.getSum());
                statement.setDouble(9, agg.getMin());
                statement.setDouble(10, agg.getMax());
                statement.addBatch();
            } catch (SQLException e) {
                throw new IOException("Failed to stage aggregate upsert", e);
            }
            if (++pending >= batchSize) {
                flush(false);
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            if (pending == 0) {
                return;
            }
            try {
                statement.executeBatch();
                connection.commit();
                pending = 0;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw new IOException("PostgreSQL upsert batch failed — job will restart and replay", e);
            }
        }

        @Override
        public void close() throws IOException {
            try (connection; statement) {
                flush(true);
            } catch (SQLException e) {
                LOG.warn("Error closing PostgreSQL writer: {}", e.getMessage());
            }
        }
    }
}
