package com.example.raglab.application;

import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkSearch;
import org.springframework.stereotype.Service;

/** Finds indexed text chunks for retrieval clients. */
@Service
public class ChunkSearchService {

    private final ElasticsearchChunkSearch search;

    public ChunkSearchService(ElasticsearchChunkSearch search) {
        this.search = search;
    }

    public PagedResult<ChunkSearchHit> search(ChunkSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search parameters are required");
        }
        return search.search(request);
    }
}
