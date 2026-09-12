package com.example.orderservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.api.dto.OrderItemResponse;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.api.dto.PageResponse;
import com.example.orderservice.config.SecurityConfig;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.exception.InvalidStatusTransitionException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.service.CreationResult;
import com.example.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice tests: serialization, validation, status codes and ProblemDetail mapping.
 * Business behavior is covered by {@code OrderServiceTest}; here the service is mocked.
 * SecurityConfig is imported with no issuer configured → the open fallback chain is
 * active, matching local/compose behavior. JWT enforcement: {@link SecurityFilterChainTest}.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");
    private static final UUID CUSTOMER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final Instant AT = Instant.parse("2026-02-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse sampleResponse(OrderStatus status) {
        return new OrderResponse(ORDER_ID, CUSTOMER_ID, status, "USD", new BigDecimal("109.88"), AT, AT, List.of(
                new OrderItemResponse(UUID.randomUUID(), "SKU-1001", "Mechanical Keyboard", 1,
                        new BigDecimal("89.90"), new BigDecimal("89.90")),
                new OrderItemResponse(UUID.randomUUID(), "SKU-2002", "USB-C Cable", 2,
                        new BigDecimal("9.99"), new BigDecimal("19.98"))));
    }

    private static final String VALID_CREATE_BODY = """
            {
              "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
              "items": [
                {"sku": "SKU-1001", "productName": "Mechanical Keyboard", "quantity": 1, "unitPrice": 89.90},
                {"sku": "SKU-2002", "productName": "USB-C Cable", "quantity": 2, "unitPrice": 9.99}
              ]
            }
            """;

    @Test
    void createReturns201WithLocationHeaderAndBody() throws Exception {
        when(orderService.create(any(), isNull())).thenReturn(new CreationResult(sampleResponse(OrderStatus.NEW), false));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalAmount").value(109.88))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void idempotentReplayReturns200WithoutLocationHeader() throws Exception {
        when(orderService.create(any(), eq("retry-123"))).thenReturn(new CreationResult(sampleResponse(OrderStatus.NEW), true));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "retry-123")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));
    }

    @Test
    void createWithoutItemsIsRejectedWith400Problem() throws Exception {
        String body = """
                {"customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "items": []}
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors.items").exists());

        verify(orderService, never()).create(any(), any());
    }

    @Test
    void malformedJsonBodyYields400NotA500() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));

        verify(orderService, never()).create(any(), any());
    }

    @Test
    void unknownEnumStatusValueYields400NotA500() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void oversizedIdempotencyKeyYields400Problem() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k".repeat(65))
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors.idempotencyKey").exists());

        verify(orderService, never()).create(any(), any());
    }

    @Test
    void tooManyItemsYields400Problem() throws Exception {
        String item = "{\"sku\":\"S\",\"productName\":\"P\",\"quantity\":1,\"unitPrice\":1.00}";
        String body = "{\"customerId\":\"" + CUSTOMER_ID + "\",\"items\":[" + (item + ",").repeat(100) + item + "]}";

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors.items").exists());

        verify(orderService, never()).create(any(), any());
    }

    @Test
    void unknownSortPropertyYields400NotA500() throws Exception {
        when(orderService.list(isNull(), any(Pageable.class)))
                .thenThrow(new PropertyReferenceException("hack", TypeInformation.of(Order.class), List.of()));

        mockMvc.perform(get("/api/v1/orders?sort=hack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Unknown property 'hack' in sort or filter"));
    }

    @Test
    void getReturnsTheOrder() throws Exception {
        when(orderService.getById(ORDER_ID)).thenReturn(sampleResponse(OrderStatus.CONFIRMED));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[1].lineTotal").value(19.98));
    }

    @Test
    void getUnknownOrderYields404Problem() throws Exception {
        when(orderService.getById(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));
    }

    @Test
    void getWithMalformedUuidYields400Problem() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void listReturnsThePageEnvelope() throws Exception {
        OrderSummaryResponse summary = new OrderSummaryResponse(
                ORDER_ID, CUSTOMER_ID, OrderStatus.NEW, "USD", new BigDecimal("109.88"), AT);
        when(orderService.list(isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/orders?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].items").doesNotExist());
    }

    @Test
    void patchStatusReturnsTheUpdatedOrder() throws Exception {
        when(orderService.updateStatus(ORDER_ID, OrderStatus.CONFIRMED)).thenReturn(sampleResponse(OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void illegalTransitionYields409Problem() throws Exception {
        when(orderService.updateStatus(ORDER_ID, OrderStatus.CANCELLED))
                .thenThrow(new InvalidStatusTransitionException(ORDER_ID, OrderStatus.DELIVERED, OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid status transition"))
                .andExpect(jsonPath("$.from").value("DELIVERED"))
                .andExpect(jsonPath("$.to").value("CANCELLED"));
    }

    @Test
    void cancelReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNoContent());

        verify(orderService).cancel(ORDER_ID);
    }

    @Test
    void unknownRouteYields404Problem() throws Exception {
        mockMvc.perform(get("/definitely/not/a/route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void unexpectedServiceFailureYieldsSanitized500() throws Exception {
        when(orderService.getById(ORDER_ID)).thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
