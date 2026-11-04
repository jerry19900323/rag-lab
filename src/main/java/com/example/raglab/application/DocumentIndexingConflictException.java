package com.example.raglab.application;

/** Indicates that another ingestion is already updating the same logical document. */
public class DocumentIndexingConflictException extends RuntimeException {

    public DocumentIndexingConflictException(String documentId) {
        super("Document " + documentId + " is already being indexed");
    }
}
