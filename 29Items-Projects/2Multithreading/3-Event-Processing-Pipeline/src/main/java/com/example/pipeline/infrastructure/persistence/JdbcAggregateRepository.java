package com.example.pipeline.infrastructure.persistence;

import com.example.pipeline.application.port.AggregateRepository;
import com.example.pipeline.domain.AggregateSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Stub JDBC repository — the seam for persisting a run's aggregates.
 *
 * <p><strong>Deliberately unimplemented, and deliberately importing no {@code java.sql}
 * type.</strong> Two reasons:
 * <ul>
 *   <li>the project ships with <em>zero</em> runtime dependencies, and a JDBC driver
 *       would be the first one;</li>
 *   <li>the documented {@code jlink} image is {@code java.base, java.logging,
 *       jdk.httpserver} (see {@code docs/TECH-NOTES.md} §3.3). Referencing
 *       {@code java.sql} here would pull the {@code java.sql} module into the image and
 *       silently invalidate that documentation.</li>
 * </ul>
 * The DDL this class would write against is already committed under {@code /migrations},
 * so the schema is reviewable before any code exists.
 *
 * <p><strong>To finish it</strong> (see the Phase 3 TODO in {@code docs/PROJECT-PLAN.md}):
 * <ol>
 *   <li>add the {@code java.sql} requirement and a driver dependency, and extend the
 *       {@code jlink} module list;</li>
 *   <li>open one connection per {@code save} (or accept a {@code DataSource} — never a
 *       shared {@code Connection}: {@code Connection} is not thread-safe and this port is
 *       documented as thread-safe);</li>
 *   <li>insert a {@code pipeline_run} row, then batch-insert one
 *       {@code sensor_aggregate} row per sensor inside one transaction, so a partial
 *       write is never visible;</li>
 *   <li>use {@code PreparedStatement} throughout — the sensor id is data, never string
 *       concatenation.</li>
 * </ol>
 *
 * <p><strong>Nothing wires this class.</strong> It was briefly selected whenever a JDBC URL
 * was configured, which produced the worst possible failure shape: the run generated,
 * filtered and aggregated every event correctly, wrote the CSV report, and only then threw
 * from {@link #save} — reporting {@code FAILED} and exit 1 for work that had actually
 * succeeded. {@code PipelineApplication.run} now rejects a configured JDBC URL before any
 * thread starts (exit 2), so this class is reachable only from its own test, which pins the
 * contract that every method throws until it is implemented.
 */
public final class JdbcAggregateRepository implements AggregateRepository {

    private static final Logger LOG = Logger.getLogger(JdbcAggregateRepository.class.getName());

    private final String jdbcUrl;
    private final String user;
    private final String password;

    /**
     * @param jdbcUrl  JDBC URL, e.g. {@code jdbc:postgresql://localhost:5432/pipeline}
     * @param user     database user
     * @param password database password; never logged
     */
    public JdbcAggregateRepository(String jdbcUrl, String user, String password) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.user = Objects.requireNonNull(user, "user");
        this.password = Objects.requireNonNull(password, "password");
        LOG.warning("JdbcAggregateRepository is a stub; every operation on it throws");
    }

    @Override
    public void save(AggregateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        // TODO(phase-3): INSERT pipeline_run + batch INSERT sensor_aggregate in one transaction.
        throw new UnsupportedOperationException(
                "JDBC persistence is not implemented; see migrations/V001__create_pipeline_tables.sql");
    }

    @Override
    public Optional<AggregateSnapshot> findLatest() {
        // TODO(phase-3): SELECT the newest pipeline_run, then its sensor_aggregate rows.
        throw new UnsupportedOperationException("JDBC persistence is not implemented");
    }

    /** The configured URL — echoed in diagnostics; credentials are never exposed. */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    /** The configured user; the password has no accessor on purpose. */
    public String user() {
        return user;
    }

    /** Reports whether a password was supplied, without revealing it. */
    public boolean hasPassword() {
        return !password.isBlank();
    }
}
