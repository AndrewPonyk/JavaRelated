package com.shopflow.reco.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflow.common.error.GlobalExceptionHandler;
import com.shopflow.reco.domain.ProductNode;
import com.shopflow.reco.repository.RecommendationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Web-layer tests for {@link RecommendationController} (standalone MockMvc). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationControllerTest {

    @Mock
    private RecommendationRepository repository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(repository)).build();
    }

    @Test
    void alsoBought_returnsProducts() throws Exception {
        when(repository.alsoBought(eq("p1"), anyInt())).thenReturn(List.of(new ProductNode("p2", "Thing")));

        mvc.perform(get("/api/v1/recommendations/products/p1/also-bought"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("p2"));
    }

    @Test
    void forCustomer_withHistory_returnsPersonalised() throws Exception {
        when(repository.recommendedForCustomer(eq("cust-1"), anyInt()))
                .thenReturn(List.of(new ProductNode("p9", "Recommended")));

        mvc.perform(get("/api/v1/recommendations/customers/cust-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("p9"));
    }

    @Test
    void forCustomer_coldStart_fallsBackToTrending() throws Exception {
        when(repository.recommendedForCustomer(eq("newbie"), anyInt())).thenReturn(List.of());
        when(repository.trending(anyInt())).thenReturn(List.of(new ProductNode("top", "Popular")));

        mvc.perform(get("/api/v1/recommendations/customers/newbie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("top"));
    }

    @Test
    void trending_returnsProducts() throws Exception {
        when(repository.trending(anyInt())).thenReturn(List.of(new ProductNode("hot", "Hot")));

        mvc.perform(get("/api/v1/recommendations/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Hot"));
    }
}
