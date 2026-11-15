package com.example.raglab.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.raglab.application.ChunkSearchHit;
import com.example.raglab.application.ChunkSearchRequest;
import com.example.raglab.application.DocumentProfileHit;
import com.example.raglab.application.PagedResult;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Executes scored keyword searches over indexed document chunks. */
@Component
public class ElasticsearchChunkSearch {

    private final ElasticsearchClient client;

    public ElasticsearchChunkSearch(ElasticsearchClient client) {
        this.client = client;
    }

    public PagedResult<ChunkSearchHit> search(ChunkSearchRequest parameters) {
        return execute(buildRequest(parameters), parameters);
    }

    public PagedResult<ChunkSearchHit> searchByVector(
            ChunkSearchRequest parameters, List<Float> vector, String modelKey) {
        return execute(buildVectorRequest(parameters, vector, modelKey), parameters);
    }

    public List<DocumentProfileHit> searchDocumentProfiles(String query, int size) {
        if (query == null || query.isBlank() || size < 1 || size > 20)
            throw new IllegalArgumentException("A document-profile query and size from 1 to 20 are required");
        try {
            SearchResponse<ElasticsearchChunkDocument> response = client.search(
                    buildDocumentProfileRequest(query, size), ElasticsearchChunkDocument.class);
            return response.hits().hits().stream()
                    .filter(hit -> hit.source() != null && hit.source().documentProfile()
                            && hit.source().documentSummary() != null
                            && !hit.source().documentSummary().isBlank())
                    .map(hit -> new DocumentProfileHit(hit.source().documentId(),
                            hit.source().fileName(), hit.source().documentSummary(),
                            hit.score() == null ? 0.0 : hit.score()))
                    .toList();
        } catch (IOException | RuntimeException exception) {
            throw new ChunkSearchException("Could not search document summaries", exception);
        }
    }

    SearchRequest buildDocumentProfileRequest(String query, int size) {
        return new SearchRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_ALIAS)
                .size(size)
                .query(q -> q.bool(bool -> bool
                        .must(must -> must.match(match -> match.field("documentSummary").query(query)))
                        .filter(filter -> filter.term(term -> term.field("documentProfile").value(true)))))
                .build();
    }

    private PagedResult<ChunkSearchHit> execute(SearchRequest request, ChunkSearchRequest parameters) {
        try {
            SearchResponse<ElasticsearchChunkDocument> response = client.search(
                    request, ElasticsearchChunkDocument.class);
            List<ChunkSearchHit> items = response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(this::toSearchHit)
                    .toList();
            long total = response.hits().total() == null ? items.size() : response.hits().total().value();
            return PagedResult.of(items, parameters.page(), parameters.size(), total);
        } catch (IOException | RuntimeException exception) {
            throw new ChunkSearchException("Could not search document chunks", exception);
        }
    }

    SearchRequest buildVectorRequest(ChunkSearchRequest parameters, List<Float> vector, String modelKey) {
        int k = (parameters.page() + 1) * parameters.size();
        return new SearchRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_ALIAS)
                .from(parameters.page() * parameters.size())
                .size(parameters.size())
                .knn(knn -> {
                    knn.field("embedding").queryVector(vector)
                            .k(k).numCandidates(Math.min(10_000, Math.max(100, k * 2)))
                            .filter(filter -> filter.term(term -> term.field("embeddingModelKey").value(modelKey)));
                    if (parameters.documentId() != null) {
                        knn.filter(filter -> filter.term(term -> term.field("documentId")
                                .value(parameters.documentId())));
                    }
                    return knn;
                })
                .build();
    }

    SearchRequest buildRequest(ChunkSearchRequest parameters) {
        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_ALIAS)
                .from(parameters.page() * parameters.size())
                .size(parameters.size())
                .trackTotalHits(track -> track.enabled(true))
                .query(query -> query.bool(bool -> {
                    bool.must(must -> must.match(match -> match
                            .field("content")
                            .query(parameters.query())));
                    if (parameters.documentId() != null) {
                        bool.filter(filter -> filter.term(term -> term
                                .field("documentId")
                                .value(parameters.documentId())));
                    }
                    return bool;
                }));
        return request.build();
    }

    private ChunkSearchHit toSearchHit(Hit<ElasticsearchChunkDocument> hit) {
        ElasticsearchChunkDocument chunk = hit.source();
        return new ChunkSearchHit(
                chunk.documentId(), chunk.ingestionId(), chunk.fileName(), chunk.documentType(),
                chunk.chunkIndex(), chunk.content(), chunk.startOffset(), chunk.endOffset(),
                chunk.sourceReferences(), hit.score() == null ? 0.0 : hit.score());
    }
}
