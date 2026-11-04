package com.example.raglab.infrastructure.elasticsearch;

/** Indicates that a complete document ingestion could not be committed to Elasticsearch. */
public class ChunkStoreException extends RuntimeException {

    public ChunkStoreException(String message) {
        super(message);
    }

    public ChunkStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
