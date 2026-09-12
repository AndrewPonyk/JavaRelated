package com.shopflow.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflow.catalog.domain.Product;
import com.shopflow.catalog.domain.Review;
import com.shopflow.catalog.service.CatalogService;
import com.shopflow.common.error.ApiException;
import com.shopflow.common.error.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Web-layer tests for {@link CatalogController} (standalone MockMvc). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogControllerTest {

    @Mock
    private CatalogService catalog;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(catalog))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Product sampleProduct() {
        Product p = new Product();
        p.setId("p1");
        p.setSku("SKU-1");
        p.setName("Widget");
        p.setCategory("widgets");
        p.setPrice(new BigDecimal("9.99"));
        p.setCurrency("EUR");
        p.setActive(true);
        p.setStockOnHand(3);
        return p;
    }

    @Test
    void list_returns200() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 24), 1);
        when(catalog.list(nullable(String.class), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Widget"));
    }

    @Test
    void get_missing_returns404() throws Exception {
        when(catalog.get("nope")).thenThrow(ApiException.notFound("Product", "nope"));

        mvc.perform(get("/api/v1/products/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void addReview_valid_returns201() throws Exception {
        when(catalog.addReview(eq("p1"), anyString(), anyInt(), anyString()))
                .thenReturn(new Review("p1", "alice", 5, "Great!"));

        mvc.perform(post("/api/v1/products/p1/reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"author":"alice","rating":5,"text":"Great!"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author").value("alice"));
    }

    @Test
    void addReview_invalidRating_returns400() throws Exception {
        mvc.perform(post("/api/v1/products/p1/reviews").contentType(MediaType.APPLICATION_JSON).content("""
                {"author":"alice","rating":0,"text":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
