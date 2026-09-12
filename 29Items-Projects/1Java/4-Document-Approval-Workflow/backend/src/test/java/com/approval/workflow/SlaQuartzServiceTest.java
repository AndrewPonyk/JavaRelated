package com.approval.workflow;

import com.approval.workflow.services.SlaQuartzService;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(VertxExtension.class)
public class SlaQuartzServiceTest {

    private SlaQuartzService quartzService;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        quartzService = new SlaQuartzService(vertx);
        quartzService.start();
    }

    @AfterEach
    void tearDown() {
        if (quartzService != null) {
            quartzService.stop();
        }
    }

    @Test
    @DisplayName("Test schedule and cancel SLA Quartz Job")
    void testScheduleAndCancelJob(VertxTestContext testContext) {
        assertDoesNotThrow(() -> {
            String docId = "doc-test-123";
            Instant deadline = Instant.now().plusSeconds(300);
            quartzService.scheduleSlaJob(docId, 1, deadline);
            quartzService.cancelSlaJob(docId, 1);
        });
        testContext.completeNow();
    }
}
