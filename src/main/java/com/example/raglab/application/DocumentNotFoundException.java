package com.example.raglab.application;

/** Indicates that no logical document exists for a requested identifier. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("Document " + documentId + " was not found");
    }
}
