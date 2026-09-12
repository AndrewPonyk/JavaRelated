package com.rtap.api.metrics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body for registering a metric definition — validated at the edge. */
public record CreateMetricRequest(

        @NotBlank
        @Pattern(regexp = "^[a-z0-9]+(\\.[a-z0-9_-]+)*$",
                 message = "metricKey must be dot-separated lowercase segments, e.g. orders.completed")
        @Size(min = 3, max = 100)
        String metricKey,

        @NotBlank
        @Size(max = 120)
        String displayName,

        @Size(max = 20)
        String unit,           // e.g. "EUR", "count", "ms" — optional

        @Size(max = 500)
        String description     // optional
) {
}
