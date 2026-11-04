package com.example.raglab.application;

import java.time.Instant;

/** One document ingestion attempt and its terminal or current status. */
public record IngestionSummary(
        String ingestionId,
        String documentId,
        String documentHash,
        String status,
        int characterCount,
        int chunkCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt) {
}
