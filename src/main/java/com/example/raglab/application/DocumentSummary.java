package com.example.raglab.application;

import java.time.Instant;

/** Document metadata shown in lists and detail views. */
public record DocumentSummary(
        String documentId,
        String fileName,
        String documentType,
        String documentHash,
        int characterCount,
        int chunkCount,
        String currentIngestionId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String summary) {
    public DocumentSummary(String documentId, String fileName, String documentType,
            String documentHash, int characterCount, int chunkCount,
            String currentIngestionId, String status, Instant createdAt, Instant updatedAt) {
        this(documentId, fileName, documentType, documentHash, characterCount, chunkCount,
                currentIngestionId, status, createdAt, updatedAt, null);
    }
}
