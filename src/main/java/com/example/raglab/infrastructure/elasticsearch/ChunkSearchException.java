package com.example.raglab.infrastructure.elasticsearch;

/** Signals an Elasticsearch search failure. */
public class ChunkSearchException extends RuntimeException {

    public ChunkSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
