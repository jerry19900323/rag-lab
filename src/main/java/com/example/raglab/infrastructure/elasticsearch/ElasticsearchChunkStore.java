package com.example.raglab.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stores a complete document chunk generation in Elasticsearch. */
@Component
public class ElasticsearchChunkStore {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchChunkStore.class);

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchChunkStore(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * Writes a new ingestion generation, then removes older generations of the same document.
     * A failed bulk write is cleaned up without deleting the previously active generation.
     */
    public void replaceDocumentChunks(
            String documentId,
            String ingestionId,
            List<ElasticsearchChunkDocument> chunks) {
        validateGeneration(documentId, ingestionId, chunks);

        try {
            BulkResponse response = elasticsearchClient.bulk(buildBulkRequest(chunks));
            if (response.errors()) {
                String failures = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(this::describeFailure)
                        .collect(Collectors.joining("; "));
                cleanupFailedGeneration(documentId, ingestionId);
                throw new ChunkStoreException("Elasticsearch bulk write failed: " + failures);
            }
            deleteOlderGenerations(documentId, ingestionId);
        } catch (IOException exception) {
            cleanupFailedGeneration(documentId, ingestionId);
            throw new ChunkStoreException("Could not write document chunks to Elasticsearch", exception);
        }
    }

    /** Removes every indexed generation for a logical document. Safe to retry. */
    public void deleteDocumentChunks(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId is required");
        }
        try {
            var response = elasticsearchClient.deleteByQuery(new DeleteByQueryRequest.Builder()
                    .index(RagChunkIndexDefinition.INDEX_ALIAS)
                    .refresh(true)
                    .query(query -> query.term(term -> term.field("documentId").value(documentId)))
                    .build());
            if (!response.failures().isEmpty() || response.versionConflicts() > 0
                    || Boolean.TRUE.equals(response.timedOut())) {
                throw new ChunkDeleteException("Elasticsearch did not delete all document chunks");
            }
        } catch (IOException exception) {
            throw new ChunkDeleteException("Could not delete document chunks from Elasticsearch", exception);
        }
    }

    BulkRequest buildBulkRequest(List<ElasticsearchChunkDocument> chunks) {
        BulkRequest.Builder request = new BulkRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_ALIAS)
                .requireAlias(true)
                .refresh(Refresh.WaitFor);
        chunks.forEach(chunk -> request.operations(operation -> operation.index(index -> index
                .id(chunk.elasticsearchId())
                .document(chunk))));
        return request.build();
    }

    private void deleteOlderGenerations(String documentId, String ingestionId) throws IOException {
        DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_ALIAS)
                .refresh(true)
                .query(query -> query.bool(bool -> bool
                        .filter(filter -> filter.term(term -> term
                                .field("documentId")
                                .value(documentId)))
                        .mustNot(mustNot -> mustNot.term(term -> term
                                .field("ingestionId")
                                .value(ingestionId)))))
                .build();
        elasticsearchClient.deleteByQuery(request);
    }

    private void cleanupFailedGeneration(String documentId, String ingestionId) {
        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                    .index(RagChunkIndexDefinition.INDEX_ALIAS)
                    .refresh(true)
                    .query(query -> query.bool(bool -> bool
                            .filter(filter -> filter.term(term -> term
                                    .field("documentId")
                                    .value(documentId)))
                            .filter(filter -> filter.term(term -> term
                                    .field("ingestionId")
                                    .value(ingestionId)))))
                    .build();
            elasticsearchClient.deleteByQuery(request);
        } catch (IOException | RuntimeException cleanupFailure) {
            logger.error(
                    "Could not clean failed Elasticsearch ingestion {} for document {}",
                    ingestionId,
                    documentId,
                    cleanupFailure);
        }
    }

    private void validateGeneration(
            String documentId,
            String ingestionId,
            List<ElasticsearchChunkDocument> chunks) {
        if (documentId == null || documentId.isBlank() || ingestionId == null || ingestionId.isBlank()) {
            throw new IllegalArgumentException("Document and ingestion identifiers are required");
        }
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("At least one document chunk is required");
        }
        boolean mismatched = chunks.stream().anyMatch(chunk ->
                !Objects.equals(documentId, chunk.documentId())
                        || !Objects.equals(ingestionId, chunk.ingestionId()));
        if (mismatched) {
            throw new IllegalArgumentException("Every chunk must belong to the requested ingestion generation");
        }
    }

    private String describeFailure(BulkResponseItem item) {
        return item.id() + ": " + item.error().type() + " - " + item.error().reason();
    }
}
