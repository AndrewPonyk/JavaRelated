package com.shopflow.search.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflow.search.domain.ProductDocument;
import com.shopflow.search.service.SearchService;
import com.shopflow.search.service.SearchService.SearchResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Web-layer test for {@link SearchController} (standalone MockMvc). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService)).build();
    }

    @Test
    void search_returnsResults() throws Exception {
        var doc = new ProductDocument("p1", "Red Shoes", "shoes", new BigDecimal("50.00"), true);
        when(searchService.search(eq("shoes"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new SearchResult(List.of(doc), 1, 0, 20));

        mvc.perform(get("/api/v1/search?q=shoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Red Shoes"));
    }
}
