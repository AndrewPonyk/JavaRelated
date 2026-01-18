package com.healthcare.claims.api;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check and monitoring endpoints.
 */
@Path("/api/v1/health")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Health check and monitoring endpoints")
public class HealthResource {

    private static final Instant startTime = Instant.now();

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "claims-service")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String applicationVersion;

    @GET
    @Operation(summary = "Basic health check", description = "Returns OK if the service is running")
    public Response healthCheck() {
        return Response.ok(new SimpleHealthResponse("UP", applicationName)).build();
    }

    @GET
    @Path("/live")
    @Operation(summary = "Liveness probe", description = "Kubernetes liveness probe endpoint")
    public Response liveness() {
        return Response.ok(new SimpleHealthResponse("UP", "Service is alive")).build();
    }

    @GET
    @Path("/ready")
    @Operation(summary = "Readiness probe", description = "Kubernetes readiness probe - checks all dependencies")
    public Uni<Response> readiness() {
        return checkDatabaseConnection()
            .map(dbHealthy -> {
                if (dbHealthy) {
                    return Response.ok(new SimpleHealthResponse("UP", "Service is ready")).build();
                }
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new SimpleHealthResponse("DOWN", "Database connection failed"))
                    .build();
            })
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(new SimpleHealthResponse("DOWN", "Health check failed: " + e.getMessage()))
                    .build()
            );
    }

    @GET
    @Path("/detailed")
    @Operation(summary = "Detailed health check", description = "Returns detailed health information including dependencies")
    public Uni<Response> detailedHealth() {
        return checkDatabaseConnection()
            .map(dbHealthy -> {
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("status", dbHealthy ? "UP" : "DEGRADED");
                health.put("application", applicationName);
                health.put("version", applicationVersion);
                health.put("timestamp", Instant.now().toString());

                // Uptime
                Duration uptime = Duration.between(startTime, Instant.now());
                health.put("uptime", formatUptime(uptime));
                health.put("uptimeSeconds", uptime.getSeconds());

                // Component statuses
                Map<String, ComponentHealth> components = new LinkedHashMap<>();
                components.put("database", new ComponentHealth(dbHealthy ? "UP" : "DOWN", "PostgreSQL"));

                health.put("components", components);

                // JVM metrics
                health.put("jvm", getJvmMetrics());

                Response.Status status = dbHealthy ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE;
                return Response.status(status).entity(health).build();
            })
            .onFailure().recoverWithItem(e -> {
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("status", "DOWN");
                health.put("error", e.getMessage());
                health.put("timestamp", Instant.now().toString());
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(health).build();
            });
    }

    @GET
    @Path("/info")
    @Operation(summary = "Application info", description = "Returns application metadata")
    public Response info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", applicationName);
        info.put("version", applicationVersion);
        info.put("java", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));

        Duration uptime = Duration.between(startTime, Instant.now());
        info.put("uptime", formatUptime(uptime));
        info.put("startTime", startTime.toString());

        return Response.ok(info).build();
    }

    @GET
    @Path("/metrics")
    @Operation(summary = "Basic metrics", description = "Returns basic application metrics")
    public Response metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", Instant.now().toString());
        metrics.put("jvm", getJvmMetrics());

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        metrics.put("processUptime", runtimeBean.getUptime());

        return Response.ok(metrics).build();
    }

    private Uni<Boolean> checkDatabaseConnection() {
        return sessionFactory.withSession(session ->
            session.createNativeQuery("SELECT 1", Integer.class)
                .getSingleResult()
                .map(result -> result != null && result == 1)
        ).onFailure().recoverWithItem(false);
    }

    private Map<String, Object> getJvmMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> jvm = new LinkedHashMap<>();

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("totalMb", runtime.totalMemory() / (1024 * 1024));
        memory.put("freeMb", runtime.freeMemory() / (1024 * 1024));
        memory.put("usedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        memory.put("maxMb", runtime.maxMemory() / (1024 * 1024));
        memory.put("heapUsedMb", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        memory.put("heapMaxMb", memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        jvm.put("memory", memory);

        jvm.put("availableProcessors", runtime.availableProcessors());
        jvm.put("threadCount", Thread.activeCount());

        return jvm;
    }

    private String formatUptime(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public record SimpleHealthResponse(String status, String message) {}
    public record ComponentHealth(String status, String type) {}
}
