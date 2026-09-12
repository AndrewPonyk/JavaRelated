package com.rtap.api.metrics;

import com.rtap.api.metrics.dto.MetricDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests: routing, validation, and error contract.
 * The real read path (PG via Flyway migrations, Kafka ingest, SSE) is covered by AnalyticsApiIT.
 */
@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @Test
    void listsMetricDefinitions() throws Exception {
        when(metricsService.listDefinitions()).thenReturn(List.of(
                new MetricDefinition("orders.completed", "Orders completed", "count", null, Instant.EPOCH)));

        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metricKey").value("orders.completed"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(metricsService.create(any())).thenReturn(
                new MetricDefinition("orders.completed", "Orders completed", "count", null, Instant.EPOCH));

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metricKey":"orders.completed","displayName":"Orders completed","unit":"count"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/metrics/orders.completed"));
    }

    @Test
    void rejectsInvalidMetricKeyWithProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metricKey":"NOT VALID!!","displayName":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.metricKey").exists());
    }

    @Test
    void rejectsUnknownWindowParam() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/orders.completed/aggregates").param("window", "42d"))
                .andExpect(status().isBadRequest());
    }
}
