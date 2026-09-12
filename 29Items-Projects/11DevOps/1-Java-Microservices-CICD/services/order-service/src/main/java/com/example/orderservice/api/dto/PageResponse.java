package com.example.orderservice.api.dto;

import java.util.List;

/**
 * Stable pagination envelope. Spring's {@code Page} serialization is an implementation
 * detail that has changed across versions — this record is the public contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
