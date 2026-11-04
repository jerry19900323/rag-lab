package com.example.raglab.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Idempotently creates the versioned chunk index and its application alias. */
@Component
@ConditionalOnProperty(
        name = "rag.elasticsearch.initialize-index",
        havingValue = "true",
        matchIfMissing = true)
public class RagChunkIndexInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RagChunkIndexInitializer.class);

    private final ElasticsearchClient elasticsearchClient;

    public RagChunkIndexInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            initialize();
        } catch (IOException | RuntimeException exception) {
            logger.error(
                    "Could not initialize Elasticsearch index {}: {}",
                    RagChunkIndexDefinition.INDEX_NAME,
                    exception.getMessage(),
                    exception);
        }
    }

    void initialize() throws IOException {
        ElasticsearchIndicesClient indices = elasticsearchClient.indices();
        ExistsRequest existsRequest = new ExistsRequest.Builder()
                .index(RagChunkIndexDefinition.INDEX_NAME)
                .build();
        boolean indexExists = indices.exists(existsRequest)
                .value();

        if (!indexExists) {
            indices.create(RagChunkIndexDefinition.createRequest());
            logger.info(
                    "Created Elasticsearch index {} with alias {}",
                    RagChunkIndexDefinition.INDEX_NAME,
                    RagChunkIndexDefinition.INDEX_ALIAS);
            return;
        }

        indices.putMapping(RagChunkIndexDefinition.updateMappingRequest());

        ExistsAliasRequest aliasRequest = new ExistsAliasRequest.Builder()
                .name(RagChunkIndexDefinition.INDEX_ALIAS)
                .index(RagChunkIndexDefinition.INDEX_NAME)
                .build();
        boolean aliasExists = indices.existsAlias(aliasRequest)
                .value();
        if (!aliasExists) {
            PutAliasRequest putAliasRequest = new PutAliasRequest.Builder()
                    .index(RagChunkIndexDefinition.INDEX_NAME)
                    .name(RagChunkIndexDefinition.INDEX_ALIAS)
                    .build();
            indices.putAlias(putAliasRequest);
            logger.info(
                    "Attached Elasticsearch alias {} to existing index {}",
                    RagChunkIndexDefinition.INDEX_ALIAS,
                    RagChunkIndexDefinition.INDEX_NAME);
        }
    }
}
