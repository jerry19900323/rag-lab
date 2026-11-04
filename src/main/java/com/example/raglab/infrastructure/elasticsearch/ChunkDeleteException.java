package com.example.raglab.infrastructure.elasticsearch;

/** A document's indexed chunks could not be removed. */
public class ChunkDeleteException extends RuntimeException {
    public ChunkDeleteException(String message) {
        super(message);
    }

    public ChunkDeleteException(String message, Throwable cause) {
        super(message, cause);
    }
}
