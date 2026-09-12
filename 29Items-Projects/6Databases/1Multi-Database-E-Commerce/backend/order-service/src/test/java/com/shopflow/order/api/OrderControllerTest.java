package com.shopflow.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.GlobalExceptionHandler;
import com.shopflow.order.api.dto.CreateOrderRequest;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderItem;
import com.shopflow.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web-layer tests for {@link OrderController} using standalone MockMvc (controller
 * + validation + shared advice), covering the happy path and error mapping.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Order sampleOrder() {
        return Order.create("cust-1", "EUR",
                List.of(new OrderItem("p1", 2, new BigDecimal("10.00"))));
    }

    @Test
    void create_valid_returns201() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class), nullable(String.class)))
                .thenReturn(sampleOrder());

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customerId":"cust-1","currency":"EUR",
                 "items":[{"productId":"p1","quantity":2,"unitPrice":10.00}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(20.00));
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customerId":"","currency":"EUR","items":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(orderService.getOrder("missing")).thenThrow(ApiException.notFound("Order", "missing"));

        mvc.perform(get("/api/v1/orders/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void markPaid_illegalTransition_returns409() throws Exception {
        when(orderService.markPaid(eq("o1"))).thenThrow(new IllegalStateException("bad state"));

        mvc.perform(post("/api/v1/orders/o1/payment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
}
