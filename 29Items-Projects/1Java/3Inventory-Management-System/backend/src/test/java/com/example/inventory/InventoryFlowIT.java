package com.example.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "spring.quartz.auto-startup=false",
    "app.outbox.poll-delay=600000",
    "app.reconciliation.enabled=false",
    "app.security.enabled=false",
    "app.cors.allowed-origins=http://localhost"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "inventory.stock.changed",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers(disabledWithoutDocker = true)
class InventoryFlowIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("inventory")
            .withUsername("inventory")
            .withPassword("inventory");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void executesWarehouseInventoryReservationAndShipmentFlow() throws Exception {
        JsonNode warehouse = json(mvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TEST","name":"Integration warehouse"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String warehouseId = warehouse.get("id").asText();

        String createBody = """
                {"sku":"WIDGET-1","barcode":"4006381333931","symbology":"EAN_13",
                 "aliases":[],"name":"Widget","quantity":10,"reorderPoint":3,"warehouseId":"%s"}
                """.formatted(warehouseId);
        JsonNode item = json(mvc.perform(post("/api/v1/inventory")
                        .header("Idempotency-Key", "create-widget-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andReturn().getResponse().getContentAsString());
        String itemId = item.get("id").asText();

        mvc.perform(post("/api/v1/inventory/{id}/receipts", itemId)
                        .header("Idempotency-Key", "receipt-widget-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5,"reason":"Purchase order","reference":"PO-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));

        JsonNode reservation = json(mvc.perform(post("/api/v1/inventory/{id}/reservations", itemId)
                        .header("Idempotency-Key", "reserve-widget-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":4,"externalReference":"ORDER-1","reason":"Customer order"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/reservations/{id}/fulfill", reservation.get("id").asText())
                        .header("Idempotency-Key", "fulfill-widget-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        mvc.perform(get("/api/v1/inventory/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(11))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
        mvc.perform(get("/api/v1/inventory/barcode/4006381333931")
                        .param("warehouseId", warehouseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("WIDGET-1"));
        mvc.perform(get("/api/v1/inventory/{id}/movements", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"{ inventoryItem(id: \\"%s\\") { sku quantity } }"}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inventoryItem.quantity").value(11));
    }

    @Test
    void validatesRequestsAndReplaysIdempotentCreation() throws Exception {
        mvc.perform(post("/api/v1/inventory")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void executesAdvancedInventoryAndWarehouseLifecycle() throws Exception {
        JsonNode sourceWarehouse = createWarehouse("ADV-SOURCE", "Advanced source");
        JsonNode destinationWarehouse = createWarehouse("ADV-DEST", "Advanced destination");
        JsonNode emptyWarehouse = createWarehouse("ADV-EMPTY", "Empty warehouse");

        mvc.perform(get("/api/v1/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/warehouses/{id}", sourceWarehouse.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADV-SOURCE"));
        mvc.perform(put("/api/v1/warehouses/{id}", destinationWarehouse.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated destination","active":true,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated destination"));
        mvc.perform(delete("/api/v1/warehouses/{id}", emptyWarehouse.get("id").asText()))
                .andExpect(status().isNoContent());

        JsonNode source = createItem(sourceWarehouse.get("id").asText(), "ADVANCED-1", 20,
                """
                [{"barcode":"036000291452","symbology":"UPC_A","primary":false}]
                """);
        String sourceId = source.get("id").asText();
        mvc.perform(get("/api/v1/inventory")
                        .param("warehouseId", sourceWarehouse.get("id").asText())
                        .param("query", "advanced")
                        .param("active", "true")
                        .param("sort", "quantity")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/inventory/barcode/036000291452"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sourceId));
        mvc.perform(put("/api/v1/inventory/{id}", sourceId)
                        .header("Idempotency-Key", "advanced-update-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode":"4006381333931","symbology":"EAN_13",
                                 "aliases":[{"barcode":"036000291452","symbology":"UPC_A","primary":false}],
                                 "name":"Updated advanced item","reorderPoint":5,
                                 "active":true,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated advanced item"));

        mvc.perform(post("/api/v1/inventory/{id}/adjustments", sourceId)
                        .header("Idempotency-Key", "advanced-adjust-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delta":2,"reason":"Cycle count","reference":"COUNT-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(22));
        mvc.perform(post("/api/v1/inventory/{id}/receipts", sourceId)
                        .header("Idempotency-Key", "advanced-receipt-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":3,"reason":"Purchase order","reference":"PO-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25));
        mvc.perform(post("/api/v1/inventory/{id}/shipments", sourceId)
                        .header("Idempotency-Key", "advanced-shipment-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":2,"reason":"Direct shipment","reference":"SHIP-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(23));
        mvc.perform(post("/api/v1/inventory/bulk-adjustments")
                        .header("Idempotency-Key", "advanced-bulk-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[{"itemId":"%s","delta":1,
                                 "reason":"Bulk count","reference":"BULK-1"}]}
                                """.formatted(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(24));

        JsonNode reservation = json(mvc.perform(post("/api/v1/inventory/{id}/reservations", sourceId)
                        .header("Idempotency-Key", "advanced-reserve-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5,"externalReference":"ADV-ORDER-1","reason":"Allocation"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/inventory/{id}/reservations", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/v1/reservations/{id}/release", reservation.get("id").asText())
                        .header("Idempotency-Key", "advanced-release-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "advanced-transfer-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceItemId":"%s","destinationWarehouseId":"%s","quantity":4,
                                 "reason":"Rebalance","reference":""}
                """.formatted(sourceId, destinationWarehouse.get("id").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceOnHand").value(20))
                .andExpect(jsonPath("$.destinationOnHand").value(4));
        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "advanced-transfer-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceItemId":"%s","destinationWarehouseId":"%s","quantity":2,
                                 "reason":"Rebalance","reference":"TRANSFER-2"}
                """.formatted(sourceId, destinationWarehouse.get("id").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceOnHand").value(18))
                .andExpect(jsonPath("$.destinationOnHand").value(6));

        JsonNode emptyItem = createItem(sourceWarehouse.get("id").asText(), "EMPTY-1", 0, "[]");
        mvc.perform(delete("/api/v1/inventory/{id}", emptyItem.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"reason":"Retired test item"}
                                """))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/inventory/{id}/movements", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    private JsonNode createWarehouse(String code, String name) throws Exception {
        return json(mvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode createItem(String warehouseId, String sku, long quantity, String aliases) throws Exception {
        String barcode = sku.startsWith("EMPTY") ? "5901234123457" : "4006381333931";
        return json(mvc.perform(post("/api/v1/inventory")
                        .header("Idempotency-Key", "create-" + sku.toLowerCase())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","barcode":"%s","symbology":"EAN_13",
                                 "aliases":%s,"name":"%s","quantity":%d,
                                 "reorderPoint":3,"warehouseId":"%s"}
                                """.formatted(sku, barcode, aliases, sku, quantity, warehouseId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
