package com.example.raglab.application;

import java.util.List;

/** Stable page response used by list APIs. */
public record PagedResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PagedResult {
        items = List.copyOf(items);
    }

    public static <T> PagedResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PagedResult<>(items, page, size, totalElements, totalPages);
    }
}
