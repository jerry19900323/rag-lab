package com.example.raglab.application;

import java.time.Instant;

/** Persists document identity and ingestion lifecycle metadata. */
public interface DocumentMetadataStore {

    void begin(DocumentIngestionMetadata metadata);

    void complete(DocumentIngestionMetadata metadata, Instant completedAt);

    void fail(DocumentIngestionMetadata metadata, Instant failedAt, String errorMessage);

    /** Removes a logical document and its ingestion history. */
    void delete(String documentId);
}
