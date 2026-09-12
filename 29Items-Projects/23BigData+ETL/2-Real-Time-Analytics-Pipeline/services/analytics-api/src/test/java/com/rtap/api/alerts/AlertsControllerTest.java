package com.rtap.api.alerts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertsController.class)
class AlertsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertsService alertsService;

    private static AnomalyAlertDto alert(String status) {
        return new AnomalyAlertDto("a1f0c2d4-0000-0000-0000-000000000001", "orders.completed",
                "critical", 6.3, 182, 46.5, Instant.EPOCH, Instant.EPOCH, status, null);
    }

    @Test
    void listsOpenAlertsByDefault() throws Exception {
        when(alertsService.list("open", 100)).thenReturn(List.of(alert("open")));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metricKey").value("orders.completed"))
                .andExpect(jsonPath("$[0].severity").value("critical"));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").param("status", "everything"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitIsBounded() throws Exception {
        when(alertsService.list("open", 500)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts").param("limit", "500"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/alerts").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/alerts").param("limit", "5000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgeReturnsUpdatedAlert() throws Exception {
        when(alertsService.acknowledge(eq("a1f0c2d4-0000-0000-0000-000000000001"), anyString()))
                .thenReturn(alert("acknowledged"));

        mockMvc.perform(post("/api/v1/alerts/a1f0c2d4-0000-0000-0000-000000000001/ack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("acknowledged"));
    }

    @Test
    void acknowledgeUnknownAlertIsProblemDetails404() throws Exception {
        when(alertsService.acknowledge(anyString(), anyString()))
                .thenThrow(new NoSuchElementException("Unknown alert: x"));

        mockMvc.perform(post("/api/v1/alerts/x/ack"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }
}
