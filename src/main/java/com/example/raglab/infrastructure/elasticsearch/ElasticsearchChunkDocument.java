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
        String schemaVersion,
        List<Float> embedding,
        String embeddingModelKey,
        String documentSummary,
        boolean documentProfile) {

    public static final String SCHEMA_VERSION = "v1";

    public ElasticsearchChunkDocument {
        sourceReferences = List.copyOf(sourceReferences);
    }

    public ElasticsearchChunkDocument(String documentId, String ingestionId, String fileName,
            String documentType, String documentHash, int chunkIndex, String content,
            int characterCount, int startOffset, int endOffset, List<SourceReference> sourceReferences,
            String contentHash, Instant indexedAt, String schemaVersion) {
        this(documentId, ingestionId, fileName, documentType, documentHash, chunkIndex, content,
                characterCount, startOffset, endOffset, sourceReferences, contentHash, indexedAt,
                schemaVersion, null, null, null, false);
    }

    public ElasticsearchChunkDocument(String documentId, String ingestionId, String fileName,
            String documentType, String documentHash, int chunkIndex, String content,
            int characterCount, int startOffset, int endOffset, List<SourceReference> sourceReferences,
            String contentHash, Instant indexedAt, String schemaVersion,
            List<Float> embedding, String embeddingModelKey) {
        this(documentId, ingestionId, fileName, documentType, documentHash, chunkIndex, content,
                characterCount, startOffset, endOffset, sourceReferences, contentHash, indexedAt,
                schemaVersion, embedding, embeddingModelKey, null, false);
    }

    public ElasticsearchChunkDocument(String documentId, String ingestionId, String fileName,
            String documentType, String documentHash, int chunkIndex, String content,
            int characterCount, int startOffset, int endOffset, List<SourceReference> sourceReferences,
            String contentHash, Instant indexedAt, String schemaVersion,
            List<Float> embedding, String embeddingModelKey, String documentSummary) {
        this(documentId, ingestionId, fileName, documentType, documentHash, chunkIndex, content,
                characterCount, startOffset, endOffset, sourceReferences, contentHash, indexedAt,
                schemaVersion, embedding, embeddingModelKey, documentSummary, false);
    }

    /** Returns the deterministic Elasticsearch document identifier. */
    public String elasticsearchId() {
        return documentId + ":" + ingestionId + ":" + chunkIndex;
    }
}
