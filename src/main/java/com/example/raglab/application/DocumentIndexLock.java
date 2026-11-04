package com.example.raglab.application;

/** Serializes ingestion operations for one logical document. */
public interface DocumentIndexLock {

    LockHandle acquire(String documentId);

    interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }
}
