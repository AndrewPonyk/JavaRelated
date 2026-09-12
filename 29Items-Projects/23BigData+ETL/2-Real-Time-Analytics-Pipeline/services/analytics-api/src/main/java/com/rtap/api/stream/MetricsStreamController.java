package com.rtap.api.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live stream endpoint: pushes {@code aggregate} and {@code alert} events (raw topic
 * JSON) plus {@code heartbeat}s to the dashboard. Event payloads originate from the
 * read_committed Kafka listeners ({@link KafkaIngestListeners}).
 */
@RestController
public class MetricsStreamController {

    private final SseBroadcaster broadcaster;

    public MetricsStreamController(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/api/v1/stream/metrics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
