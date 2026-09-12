package com.example.inventory.forecast;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory/{itemId}/forecast")
public class ForecastController {

    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public ForecastResponse latest(@PathVariable UUID itemId) {
        return service.latest(itemId);
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('SCOPE_inventory:admin')")
    public ForecastResponse refresh(@PathVariable UUID itemId,
                                    @RequestParam(defaultValue = "14") @Min(1) @Max(90) int horizonDays) {
        return service.refresh(itemId, horizonDays);
    }
}
