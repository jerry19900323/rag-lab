package com.example.raglab.infrastructure.document;

/** Normalized text and metadata produced by a document parser. */
public record ParsedDocument(String fileName, String documentType, String content) {
}
