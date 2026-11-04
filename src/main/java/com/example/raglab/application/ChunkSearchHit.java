package com.example.raglab.application;

import com.example.raglab.domain.SourceReference;
import java.util.List;

/** A matching indexed chunk with its source location and relevance score. */
public record ChunkSearchHit(
        String documentId,
        String ingestionId,
        String fileName,
        String documentType,
        int chunkIndex,
        String content,
        int startOffset,
        int endOffset,
        List<SourceReference> sourceReferences,
        double score) {

    public ChunkSearchHit {
        sourceReferences = List.copyOf(sourceReferences);
    }
}
