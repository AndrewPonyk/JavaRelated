package com.example.pipeline.infrastructure.http;

import com.example.pipeline.infrastructure.concurrent.NamedThreadFactory;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lifecycle wrapper around the JDK's {@link HttpServer} for the optional control plane.
 *
 * <p><strong>Why {@code com.sun.net.httpserver} and not a framework.</strong> The project
 * has zero runtime dependencies. This endpoint serves four routes to a developer on
 * {@code localhost}; the {@code jdk.httpserver} module is already in the JDK, so it costs
 * one module in the {@code jlink} image and nothing else. Spring Boot or Jetty would be
 * two orders of magnitude more machinery — and a supply chain — for the same four routes.
 *
 * <p><strong>Loopback only, and enforced here rather than documented.</strong> The bind
 * address is {@link InetAddress#getLoopbackAddress()}, never a wildcard, so the socket is
 * unreachable from another host regardless of firewall state. That containment is what
 * makes a single shared token a proportionate authentication scheme (see
 * {@code docs/ARCHITECTURE.md} §2.5); if this ever needs to bind a routable interface, the
 * token must be replaced first.
 *
 * <p><strong>Its own small executor.</strong> A fixed pool of {@link #HANDLER_THREADS}
 * daemon threads serves requests. Two things follow: a slow or stalled client can never
 * occupy a pipeline thread, and because the threads are daemons, a forgotten
 * {@link #stop()} cannot keep the JVM alive after the run finishes. The default
 * {@code HttpServer} executor runs handlers on the dispatcher thread, which would
 * serialise every request behind the slowest one.
 *
 * <p>Disabled by default — {@code PipelineApplication} only constructs this when
 * {@code pipeline.http.enabled=true}, which in turn requires a token of at least 16
 * characters (validated in {@code PipelineConfig.Builder}).
 */
public final class ControlPlaneServer implements AutoCloseable {

    /** Requests are tiny and rare; a handful of threads is plenty and bounds the cost. */
    public static final int HANDLER_THREADS = 2;

    /** Seconds {@link #stop()} allows in-flight exchanges to finish. */
    public static final int STOP_GRACE_SECONDS = 1;

    /** Listen backlog; small on purpose, this is a single-developer diagnostic surface. */
    private static final int BACKLOG = 8;

    private static final Logger LOG = Logger.getLogger(ControlPlaneServer.class.getName());

    private final HttpServer server;
    private final ExecutorService handlerExecutor;
    private volatile boolean started;

    /**
     * Binds the socket immediately so a port clash fails at startup rather than silently
     * leaving the operator without a control plane.
     *
     * @param port    TCP port on the loopback interface
     * @param handler handler for every route
     * @throws UncheckedIOException if the port cannot be bound
     */
    public ControlPlaneServer(int port, HttpHandler handler) {
        Objects.requireNonNull(handler, "handler");
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        try {
            this.server = HttpServer.create(address, BACKLOG);
        } catch (IOException e) {
            throw new UncheckedIOException("could not bind control plane to " + address, e);
        }
        this.handlerExecutor = Executors.newFixedThreadPool(
                HANDLER_THREADS, new NamedThreadFactory("pipeline-http", true));
        this.server.setExecutor(handlerExecutor);
        // One handler, all paths: routing lives in PipelineControlHandler so that an
        // unknown path returns a 404 *after* the token check, not a 404 that reveals which
        // paths exist to an unauthenticated caller.
        this.server.createContext("/", handler);
    }

    /** Starts accepting requests. Idempotent. */
    public void start() {
        if (started) {
            return;
        }
        server.start();
        started = true;
        LOG.info("control plane listening on http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort());
    }

    /**
     * Stops the listener and its executor. Idempotent, and safe to call from a shutdown
     * hook.
     *
     * <p>{@code HttpServer.stop} closes the listening socket at once and then blocks up to
     * the grace period for in-flight exchanges. The executor is shut down afterwards
     * because {@code stop} does not touch it.
     */
    public void stop() {
        if (!started) {
            handlerExecutor.shutdownNow();
            return;
        }
        started = false;
        try {
            server.stop(STOP_GRACE_SECONDS);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "control plane did not stop cleanly", e);
        }
        handlerExecutor.shutdown();
        try {
            if (!handlerExecutor.awaitTermination(STOP_GRACE_SECONDS, TimeUnit.SECONDS)) {
                handlerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            handlerExecutor.shutdownNow();
            // Never swallow an interrupt: the caller (often a shutdown hook) needs to see it.
            Thread.currentThread().interrupt();
        }
        LOG.info("control plane stopped");
    }

    /** The bound port — useful in tests, which pass {@code 0} to get an ephemeral one. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Whether {@link #start()} has run and {@link #stop()} has not. */
    public boolean isRunning() {
        return started;
    }

    @Override
    public void close() {
        stop();
    }
}
