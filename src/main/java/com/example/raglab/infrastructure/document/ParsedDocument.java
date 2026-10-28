package com.example.raglab.infrastructure.document;

import java.util.List;

/** Normalized text and traceable source structure produced by a document parser. */
public record ParsedDocument(
        String fileName,
        String documentType,
        String content,
        List<SourceSegment> sourceSegments) {

    public ParsedDocument {
        sourceSegments = List.copyOf(sourceSegments);
    }
}
