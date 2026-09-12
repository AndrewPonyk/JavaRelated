package com.example.pipeline.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Lifecycle and binding contract of the control-plane listener.
 *
 * <p>The handler's behaviour is covered by {@link PipelineControlHandlerTest}; this class
 * tests only what the wrapper itself promises — loopback binding, an ephemeral port on
 * {@code 0}, idempotent {@code start}/{@code stop}, daemon handler threads, and a bind
 * clash surfacing at construction rather than as a missing control plane nobody notices.
 */
@Timeout(20)
@DisplayName("ControlPlaneServer")
class ControlPlaneServerTest {

    /** Answers 204 to anything; the handler is not what is under test here. */
    private static final HttpHandler NO_CONTENT = exchange -> {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    };

    @Test
    @DisplayName("port 0 binds an ephemeral port that port() reports")
    void ephemeralPortIsReported() {
        try (ControlPlaneServer server = new ControlPlaneServer(0, NO_CONTENT)) {
            server.start();
            assertTrue(server.port() > 0, "expected a real port, got " + server.port());
            assertTrue(server.isRunning());
        }
    }

    /**
     * The security property the single shared token depends on: the socket must not be
     * reachable from a routable interface. Asserted by connecting to loopback (must work)
     * and to the machine's own non-loopback address (must not).
     */
    @Test
    @DisplayName("the listener is bound to loopback, not to a wildcard address")
    void listenerIsLoopbackOnly() throws Exception {
        try (ControlPlaneServer server = new ControlPlaneServer(0, NO_CONTENT)) {
            server.start();
            try (Socket loopback = new Socket()) {
                loopback.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(),
                        server.port()), 5_000);
                assertTrue(loopback.isConnected(), "loopback must be reachable");
            }
            InetAddress routable = routableAddress();
            if (routable == null) {
                return; // Host has no non-loopback address; nothing to prove against.
            }
            try (Socket external = new Socket()) {
                assertThrows(IOException.class, () -> external.connect(
                        new java.net.InetSocketAddress(routable, server.port()), 2_000),
                        "a wildcard bind would have accepted this connection");
            }
        }
    }

    @Test
    @DisplayName("start and stop are both idempotent")
    void startAndStopAreIdempotent() {
        ControlPlaneServer server = new ControlPlaneServer(0, NO_CONTENT);
        server.start();
        int port = server.port();
        server.start();
        assertEquals(port, server.port(), "a second start must not rebind");
        server.stop();
        assertFalse(server.isRunning());
        server.stop();
        server.close();
        assertFalse(server.isRunning());
    }

    @Test
    @DisplayName("stopping a server that never started still releases its executor")
    void stoppingAnUnstartedServerIsSafe() {
        ControlPlaneServer server = new ControlPlaneServer(0, NO_CONTENT);
        assertFalse(server.isRunning());
        server.close();
        assertFalse(server.isRunning());
    }

    @Test
    @DisplayName("after stop() the port no longer accepts connections")
    void stopClosesTheListener() throws Exception {
        ControlPlaneServer server = new ControlPlaneServer(0, NO_CONTENT);
        server.start();
        int port = server.port();
        server.stop();
        try (Socket socket = new Socket()) {
            assertThrows(IOException.class, () -> socket.connect(
                    new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000));
        }
    }

    @Test
    @DisplayName("a port already in use fails at construction, not silently")
    void portClashFailsFast() {
        try (ControlPlaneServer first = new ControlPlaneServer(0, NO_CONTENT)) {
            first.start();
            UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                    () -> new ControlPlaneServer(first.port(), NO_CONTENT));
            assertTrue(thrown.getMessage().contains("could not bind"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a null handler is rejected")
    void nullHandlerIsRejected() {
        assertThrows(NullPointerException.class, () -> new ControlPlaneServer(0, null));
    }

    /**
     * Handler threads must be daemons: a forgotten {@code stop()} would otherwise keep the
     * JVM alive after the run finished, turning a completed pipeline into a hung process.
     */
    @Test
    @DisplayName("requests are served on a daemon thread named for the pool")
    void handlersRunOnNamedDaemonThreads() throws Exception {
        StringBuilder observed = new StringBuilder();
        HttpHandler recorder = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Thread current = Thread.currentThread();
                synchronized (observed) {
                    observed.append(current.getName()).append('|').append(current.isDaemon());
                }
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        };
        try (ControlPlaneServer server = new ControlPlaneServer(0, recorder)) {
            server.start();
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> response = client.send(
                    java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://127.0.0.1:" + server.port() + "/x")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
        String seen = observed.toString();
        assertTrue(seen.contains("pipeline-http"), "expected a named pool thread, saw: " + seen);
        assertTrue(seen.endsWith("true"), "handler threads must be daemons, saw: " + seen);
    }

    /** First non-loopback, non-virtual address, or {@code null} on an isolated host. */
    private static InetAddress routableAddress() throws java.net.SocketException {
        var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            java.net.NetworkInterface candidate = interfaces.nextElement();
            if (!candidate.isUp() || candidate.isLoopback()) {
                continue;
            }
            var addresses = candidate.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                    return address;
                }
            }
        }
        return null;
    }
}
