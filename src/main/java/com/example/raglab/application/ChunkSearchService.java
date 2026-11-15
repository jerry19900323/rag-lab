package com.example.raglab.application;

import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkSearch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Finds indexed text chunks for retrieval clients. */
@Service
public class ChunkSearchService {
    // ES cosine kNN scores are (1 + cosine similarity) / 2. Tune with real query logs.
    private static final double MIN_VECTOR_SCORE_FOR_ANSWER = 0.8;

    private final ElasticsearchChunkSearch search;
    private final EmbeddingService embeddingService;

    public ChunkSearchService(ElasticsearchChunkSearch search, EmbeddingService embeddingService) {
        this.search = search;
        this.embeddingService = embeddingService;
    }

    public PagedResult<ChunkSearchHit> search(ChunkSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search parameters are required");
        }
        if (embeddingService.configured()) {
            return search.searchByVector(request, embeddingService.embed(request.query()),
                    embeddingService.modelKey());
        }
        return search.search(request);
    }

    /** Merges lexical and semantic candidates for answer generation using reciprocal rank. */
    public List<ChunkSearchHit> searchForAnswer(ChunkSearchRequest request) {
        return answerSearchSession(request.query()).search(request);
    }

    /** Reuses one query embedding while searching several document scopes. */
    public AnswerSearchSession answerSearchSession(String query) {
        if (query == null || query.isBlank())
            throw new IllegalArgumentException("Search query is required");
        boolean configured = embeddingService.configured();
        return new AnswerSearchSession(query, configured,
                configured ? embeddingService.embed(query) : null,
                configured ? embeddingService.modelKey() : null);
    }

    public final class AnswerSearchSession {
        private final String query;
        private final boolean embeddingConfigured;
        private final List<Float> vector;
        private final String modelKey;

        private AnswerSearchSession(String query, boolean embeddingConfigured,
                List<Float> vector, String modelKey) {
            this.query = query;
            this.embeddingConfigured = embeddingConfigured;
            this.vector = vector;
            this.modelKey = modelKey;
        }

        public List<ChunkSearchHit> search(ChunkSearchRequest request) {
            if (!query.equals(request.query()))
                throw new IllegalArgumentException("Search query differs from the prepared query");
            return mergeForAnswer(request, embeddingConfigured, vector, modelKey);
        }
    }

    private List<ChunkSearchHit> mergeForAnswer(ChunkSearchRequest request,
            boolean embeddingConfigured, List<Float> vector, String modelKey) {
        List<ChunkSearchHit> lexical = search.search(request).items();
        if (!embeddingConfigured) return lexical;
        List<ChunkSearchHit> semantic = search.searchByVector(request, vector, modelKey).items().stream()
                .filter(hit -> hit.score() >= MIN_VECTOR_SCORE_FOR_ANSWER)
                .toList();
        Map<String, ChunkSearchHit> hits = new LinkedHashMap<>();
        Map<String, Double> ranks = new LinkedHashMap<>();
        addRanks(lexical, hits, ranks);
        addRanks(semantic, hits, ranks);
        return hits.entrySet().stream()
                .sorted((left, right) -> Double.compare(ranks.get(right.getKey()), ranks.get(left.getKey())))
                .limit(request.size())
                .map(Map.Entry::getValue)
                .toList();
    }

    private void addRanks(List<ChunkSearchHit> candidates, Map<String, ChunkSearchHit> hits,
            Map<String, Double> ranks) {
        for (int index = 0; index < candidates.size(); index++) {
            ChunkSearchHit hit = candidates.get(index);
            String key = hit.documentId() + ":" + hit.ingestionId() + ":" + hit.chunkIndex();
            hits.putIfAbsent(key, hit);
            ranks.merge(key, 1.0 / (60 + index + 1), Double::sum);
        }
    }
}
