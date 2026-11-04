package com.example.raglab.application;

/** Validated keyword search parameters. */
public record ChunkSearchRequest(String query, String documentId, int page, int size) {

    public ChunkSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        query = query.trim();
        if (query.length() > 500) {
            throw new IllegalArgumentException("query must not exceed 500 characters");
        }
        if (documentId != null) {
            documentId = documentId.trim();
            if (documentId.isEmpty()) {
                documentId = null;
            } else if (documentId.length() > 200) {
                throw new IllegalArgumentException("documentId must not exceed 200 characters");
            }
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (((long) page + 1) * size > 10_000) {
            throw new IllegalArgumentException("The requested search page exceeds the 10000-result limit");
        }
    }
}
