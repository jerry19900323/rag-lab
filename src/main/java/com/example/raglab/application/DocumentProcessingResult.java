package com.example.raglab.application;

import com.example.raglab.domain.DocumentChunk;
import java.util.List;

/** Complete preview returned after parsing and chunking a document. */
public record DocumentProcessingResult(
        String fileName,
        String documentType,
        int characterCount,
        int chunkCount,
        int maxCharacters,
        int overlapCharacters,
        List<DocumentChunk> chunks) {

    public DocumentProcessingResult {
        chunks = List.copyOf(chunks);
    }
}
