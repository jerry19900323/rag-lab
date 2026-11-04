package com.example.raglab.application;

/** A concurrent document operation prevents deletion. */
public class DocumentDeletionConflictException extends RuntimeException {
    public DocumentDeletionConflictException(String documentId) {
        super("Document " + documentId + " is already being indexed or deleted");
    }
}
