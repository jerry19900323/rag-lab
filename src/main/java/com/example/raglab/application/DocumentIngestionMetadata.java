package com.example.raglab.application;

import java.time.Instant;

/** Relational metadata describing one parsed document ingestion generation. */
public record DocumentIngestionMetadata(
        String documentId,
        String ingestionId,
        String fileName,
        String documentType,
        String documentHash,
        int characterCount,
        int chunkCount,
        Instant startedAt,
        String documentSummary) {
    public DocumentIngestionMetadata(String documentId, String ingestionId, String fileName,
            String documentType, String documentHash, int characterCount, int chunkCount,
            Instant startedAt) {
        this(documentId, ingestionId, fileName, documentType, documentHash,
                characterCount, chunkCount, startedAt, null);
    }
}
