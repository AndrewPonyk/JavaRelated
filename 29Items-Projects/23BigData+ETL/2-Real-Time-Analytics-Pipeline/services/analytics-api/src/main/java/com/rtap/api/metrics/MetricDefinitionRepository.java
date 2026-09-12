package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.MetricDefinition;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** PostgreSQL access to the metric registry (metric_definitions, V001). */
@Repository
public class MetricDefinitionRepository {

    private static final RowMapper<MetricDefinition> MAPPER = (rs, rowNum) -> new MetricDefinition(
            rs.getString("metric_key"),
            rs.getString("display_name"),
            rs.getString("unit"),
            rs.getString("description"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    public MetricDefinitionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<MetricDefinition> findAll() {
        return jdbc.sql("""
                        SELECT metric_key, display_name, unit, description, created_at
                        FROM metric_definitions ORDER BY metric_key
                        """)
                .query(MAPPER)
                .list();
    }

    public Optional<MetricDefinition> find(String metricKey) {
        return jdbc.sql("""
                        SELECT metric_key, display_name, unit, description, created_at
                        FROM metric_definitions WHERE metric_key = :metricKey
                        """)
                .param("metricKey", metricKey)
                .query(MAPPER)
                .optional();
    }

    /** @return false when the key already exists (conflict decided by the DB, not a racy pre-check) */
    public boolean insert(MetricDefinition definition) {
        int rows = jdbc.sql("""
                        INSERT INTO metric_definitions (metric_key, display_name, unit, description, created_at)
                        VALUES (:metricKey, :displayName, :unit, :description, :createdAt)
                        ON CONFLICT (metric_key) DO NOTHING
                        """)
                .param("metricKey", definition.metricKey())
                .param("displayName", definition.displayName())
                .param("unit", definition.unit())
                .param("description", definition.description())
                .param("createdAt", Timestamp.from(definition.createdAt()))
                .update();
        return rows == 1;
    }
}
