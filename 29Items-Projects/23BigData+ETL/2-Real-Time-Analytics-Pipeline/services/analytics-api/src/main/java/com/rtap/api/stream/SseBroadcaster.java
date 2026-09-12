package com.rtap.api.stream;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Server-Sent Events fan-out hub. SSE over WebSocket is deliberate — one-directional
 * fan-out, plain HTTP (ADR #7). Heartbeats keep connections under LB idle timeouts;
 * EventSource auto-reconnects and the UI shows a "live paused" pill meanwhile.
 */
@Component
public class SseBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(SseBroadcaster.class);
    private static final long NO_TIMEOUT = 0L;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseBroadcaster() {
        heartbeat.scheduleAtFixedRate(() -> broadcast("heartbeat", "{}"), 15, 15, TimeUnit.SECONDS);
    }

    /** Registers a subscriber; sends an immediate {@code connected} event as the handshake. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
        LOG.debug("SSE subscriber added ({} active)", emitters.size());
        return emitter;
    }

    /** Fans a named event out to every subscriber; dead emitters are pruned. */
    public void broadcast(String eventName, String json) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    public int activeSubscribers() {
        return emitters.size();
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        emitters.forEach(SseEmitter::complete);
    }
}
