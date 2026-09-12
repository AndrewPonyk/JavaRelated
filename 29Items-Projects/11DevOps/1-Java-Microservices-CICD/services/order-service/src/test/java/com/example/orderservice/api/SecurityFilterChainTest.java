package com.example.orderservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.PageResponse;
import com.example.orderservice.config.SecurityConfig;
import com.example.orderservice.domain.OrderStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the JWT chain is enforced when an issuer is configured:
 * 401 without a token, 403 without the right scope, 200/201 with it,
 * and the documented public paths stay reachable.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com/realms/orders")
class SecurityFilterChainTest {

    private static final String VALID_CREATE_BODY = """
            {
              "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
              "items": [
                {"sku": "SKU-1001", "productName": "Mechanical Keyboard", "quantity": 1, "unitPrice": 89.90}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    /**
     * Keeps startup off the network: without this mock, Boot would build a real
     * decoder from the (fake) issuer. Tests inject authentication directly via
     * the {@code jwt()} post-processor, so the decoder is never invoked.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority scope(String name) {
        return new SimpleGrantedAuthority("SCOPE_" + name);
    }

    @Test
    void readWithoutTokenIsRejected401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).list(any(), any());
    }

    @Test
    void readWithReadScopeIsAllowed() throws Exception {
        when(orderService.list(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/orders").with(jwt().authorities(scope("orders:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void writeWithoutTokenIsRejected401() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeWithOnlyReadScopeIsForbidden403() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(scope("orders:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isForbidden());

        verify(orderService, never()).create(any(), any());
    }

    @Test
    void writeWithWriteScopeIsAllowed201() throws Exception {
        OrderResponse response = new OrderResponse(
                UUID.randomUUID(), UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"),
                OrderStatus.NEW, "USD", new BigDecimal("89.90"),
                Instant.parse("2026-02-01T12:00:00Z"), Instant.parse("2026-02-01T12:00:00Z"), List.of());
        when(orderService.create(any(), isNull())).thenReturn(new CreationResult(response, false));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwt().authorities(scope("orders:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void apiDocsPathIsPublicEvenWithJwtEnforced() throws Exception {
        // springdoc isn't mapped inside the web slice, so a permitted request falls
        // through to 404 — the point is that it is NOT 401 (no auth challenge).
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
