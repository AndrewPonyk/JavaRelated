package com.rtap.api.alerts;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL access to the alert workflow (anomaly_alerts, V001). */
@Repository
public class AnomalyAlertRepository {

    private static final RowMapper<AnomalyAlertDto> MAPPER = (rs, rowNum) -> new AnomalyAlertDto(
            rs.getString("alert_id"),
            rs.getString("metric_key"),
            rs.getString("severity"),
            rs.getDouble("score"),
            rs.getDouble("observed"),
            rs.getDouble("expected"),
            rs.getTimestamp("window_start").toInstant(),
            rs.getTimestamp("detected_at").toInstant(),
            rs.getString("status"),
            rs.getString("acked_by"));

    private static final String SELECT = """
            SELECT alert_id, metric_key, severity, score, observed, expected,
                   window_start, detected_at, status, acked_by
            FROM anomaly_alerts
            """;

    private final JdbcClient jdbc;

    public AnomalyAlertRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent ingest from {@code alerts.anomalies.v1}: the alertId minted by the
     * detector is the primary key, so Kafka redeliveries insert exactly one row.
     *
     * @return true when a new row was inserted (drives SSE fan-out)
     */
    public boolean insert(AlertMessage alert) {
        int rows = jdbc.sql("""
                        INSERT INTO anomaly_alerts
                          (alert_id, metric_key, detector, model_version, score, threshold,
                           observed, expected, severity, window_start, window_end, detected_at, dimensions)
                        VALUES (:alertId, :metricKey, :detector, :modelVersion, :score, :threshold,
                                :observed, :expected, :severity, :windowStart, :windowEnd, :detectedAt,
                                CAST(:dimensions AS jsonb))
                        ON CONFLICT (alert_id) DO NOTHING
                        """)
                .param("alertId", UUID.fromString(alert.alertId()))
                .param("metricKey", alert.metricKey())
                .param("detector", alert.detector())
                .param("modelVersion", alert.modelVersion() == null ? "-" : alert.modelVersion())
                .param("score", alert.score())
                .param("threshold", alert.threshold())
                .param("observed", alert.observed())
                .param("expected", alert.expected())
                .param("severity", alert.severity())
                .param("windowStart", Timestamp.from(Instant.ofEpochMilli(alert.windowStart())))
                .param("windowEnd", Timestamp.from(Instant.ofEpochMilli(alert.windowEnd())))
                .param("detectedAt", Timestamp.from(Instant.ofEpochMilli(alert.detectedAt())))
                .param("dimensions", alert.dimensionsJson())
                .update();
        return rows == 1;
    }

    /** {@code status} is pre-validated by the controller; both filters are parameterized. */
    public List<AnomalyAlertDto> list(String status, int limit) {
        if ("all".equals(status)) {
            return jdbc.sql(SELECT + " ORDER BY detected_at DESC LIMIT :limit")
                    .param("limit", limit)
                    .query(MAPPER).list();
        }
        return jdbc.sql(SELECT + " WHERE status = :status ORDER BY detected_at DESC LIMIT :limit")
                .param("status", status)
                .param("limit", limit)
                .query(MAPPER).list();
    }

    public Optional<AnomalyAlertDto> find(String alertId) {
        return jdbc.sql(SELECT + " WHERE alert_id = :alertId")
                .param("alertId", UUID.fromString(alertId))
                .query(MAPPER)
                .optional();
    }

    /** Optimistic transition open → acknowledged; a no-op when already acknowledged/resolved. */
    public boolean acknowledge(String alertId, String user) {
        int rows = jdbc.sql("""
                        UPDATE anomaly_alerts
                        SET status = 'acknowledged', acked_by = :user, acked_at = now()
                        WHERE alert_id = :alertId AND status = 'open'
                        """)
                .param("alertId", UUID.fromString(alertId))
                .param("user", user)
                .update();
        return rows == 1;
    }
}
