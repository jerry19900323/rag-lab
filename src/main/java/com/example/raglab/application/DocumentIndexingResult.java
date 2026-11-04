package com.example.raglab.application;

import java.time.Instant;

/** Summary of a document generation successfully committed to the search index. */
public record DocumentIndexingResult(
        String documentId,
        String ingestionId,
        String fileName,
        String documentType,
        String documentHash,
        int characterCount,
        int chunkCount,
        Instant indexedAt) {
}
