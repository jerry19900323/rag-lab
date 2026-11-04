package com.example.raglab.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.raglab.application.ChunkSearchHit;
import com.example.raglab.application.ChunkSearchRequest;
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
        try {
            SearchResponse<ElasticsearchChunkDocument> response = client.search(
                    buildRequest(parameters), ElasticsearchChunkDocument.class);
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
