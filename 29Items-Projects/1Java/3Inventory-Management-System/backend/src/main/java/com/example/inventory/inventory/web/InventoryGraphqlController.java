package com.example.inventory.inventory.web;

import com.example.inventory.forecast.ForecastResponse;
import com.example.inventory.forecast.ForecastService;
import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.inventory.service.InventoryService;
import com.example.inventory.stock.dto.StockMovementResponse;
import com.example.inventory.stock.service.StockService;
import com.example.inventory.warehouse.WarehouseResponse;
import com.example.inventory.warehouse.WarehouseService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class InventoryGraphqlController {

    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final StockService stockService;
    private final ForecastService forecastService;

    public InventoryGraphqlController(InventoryService inventoryService,
                                      WarehouseService warehouseService,
                                      StockService stockService,
                                      ForecastService forecastService) {
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
        this.stockService = stockService;
        this.forecastService = forecastService;
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public List<InventoryResponse> inventoryItems(@Argument UUID warehouseId, @Argument String query,
                                                   @Argument Boolean active, @Argument int page,
                                                   @Argument int size) {
        return inventoryService.list(warehouseId, query, active,
                pageRequest(page, size, 100, Sort.by("sku"))).getContent();
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public InventoryResponse inventoryItem(@Argument UUID id) {
        return inventoryService.get(id);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public List<WarehouseResponse> warehouses(@Argument int page, @Argument int size) {
        return warehouseService.list(pageRequest(page, size, 100, Sort.by("code"))).getContent();
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public List<StockMovementResponse> stockMovements(@Argument UUID itemId, @Argument int page,
                                                       @Argument int size) {
        return stockService.movements(itemId, pageRequest(page, size, 200, Sort.unsorted())).getContent();
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public ForecastResponse latestForecast(@Argument UUID itemId) {
        return forecastService.latest(itemId);
    }

    private PageRequest pageRequest(int page, int size, int maximumSize, Sort sort) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Page must be non-negative and size must be positive.");
        }
        return PageRequest.of(page, Math.min(size, maximumSize), sort);
    }
}
