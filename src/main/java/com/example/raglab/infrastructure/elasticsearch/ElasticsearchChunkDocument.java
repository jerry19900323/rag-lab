package com.example.raglab.infrastructure.elasticsearch;

import com.example.raglab.domain.SourceReference;
import java.time.Instant;
import java.util.List;

/** Elasticsearch representation of one searchable document chunk. */
public record ElasticsearchChunkDocument(
        String documentId,
        String ingestionId,
        String fileName,
        String documentType,
        String documentHash,
        int chunkIndex,
        String content,
        int characterCount,
        int startOffset,
        int endOffset,
        List<SourceReference> sourceReferences,
        String contentHash,
        Instant indexedAt,
        String schemaVersion) {

    public static final String SCHEMA_VERSION = "v1";

    public ElasticsearchChunkDocument {
        sourceReferences = List.copyOf(sourceReferences);
    }

    /** Returns the deterministic Elasticsearch document identifier. */
    public String elasticsearchId() {
        return documentId + ":" + ingestionId + ":" + chunkIndex;
    }
}
