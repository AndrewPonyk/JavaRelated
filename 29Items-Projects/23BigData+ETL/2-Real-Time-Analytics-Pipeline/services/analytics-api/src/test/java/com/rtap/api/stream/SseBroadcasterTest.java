package com.rtap.api.stream;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseBroadcasterTest {

    @Test
    void subscribeRegistersAndHandshakes() {
        SseBroadcaster broadcaster = new SseBroadcaster();

        SseEmitter emitter = broadcaster.subscribe();

        assertThat(emitter).isNotNull();
        assertThat(broadcaster.activeSubscribers()).isEqualTo(1);
    }

    @Test
    void deadEmittersArePrunedOnBroadcast() {
        SseBroadcaster broadcaster = new SseBroadcaster();
        SseEmitter emitter = broadcaster.subscribe();
        emitter.complete(); // client went away

        broadcaster.broadcast("aggregate", "{}");

        assertThat(broadcaster.activeSubscribers()).isZero();
    }

    @Test
    void broadcastToNobodyIsANoop() {
        SseBroadcaster broadcaster = new SseBroadcaster();
        broadcaster.broadcast("alert", "{}"); // must not throw
        assertThat(broadcaster.activeSubscribers()).isZero();
    }
}
