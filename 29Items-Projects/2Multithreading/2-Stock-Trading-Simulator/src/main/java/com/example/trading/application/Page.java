package com.example.trading.application;

import java.util.List;
import java.util.Objects;

public record Page<T>(
        List<T> items,
        int offset,
        int limit,
        int totalElements,
        boolean hasNext) {

    public static final int MAX_PAGE_SIZE = 1_000;

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (totalElements < 0 || offset > totalElements || items.size() > limit) {
            throw new IllegalArgumentException("invalid page metadata");
        }
        hasNext = offset + items.size() < totalElements;
    }

    public static <T> Page<T> from(List<T> source, int offset, int limit) {
        List<T> values = List.copyOf(Objects.requireNonNull(source, "source"));
        if (offset < 0 || offset > values.size()) {
            throw new IllegalArgumentException(
                    "offset must be between zero and the collection size");
        }
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        int end = (int) Math.min(values.size(), (long) offset + limit);
        return new Page<>(
                values.subList(offset, end),
                offset,
                limit,
                values.size(),
                end < values.size());
    }
}
