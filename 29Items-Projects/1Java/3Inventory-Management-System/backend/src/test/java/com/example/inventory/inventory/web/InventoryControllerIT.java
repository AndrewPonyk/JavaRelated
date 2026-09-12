package com.example.inventory.inventory.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.inventory.service.InventoryService;
import com.example.inventory.common.error.GlobalExceptionHandler;
import com.example.inventory.common.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InventoryControllerIT {
    @Mock private InventoryService inventoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void getsInventoryUsingServiceLayer() throws Exception {
        UUID itemId = UUID.randomUUID();
        InventoryResponse item = new InventoryResponse(itemId, "SKU-1", "0123456789012", List.of(),
                "Widget", 12, 2, 10, 4, false, true, UUID.randomUUID(), "MAIN", 3,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(inventoryService.get(itemId)).thenReturn(item);
        mockMvc.perform(get("/api/v1/inventory/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", String.valueOf((char) 34) + "3" + (char) 34))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void returnsConsistentClientErrors() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(inventoryService.get(missingId)).thenThrow(new NotFoundException("Item was not found."));

        mockMvc.perform(get("/api/v1/inventory/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mockMvc.perform(get("/api/v1/inventory").param("sort", "unsafe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        mockMvc.perform(post("/api/v1/inventory")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }
}
