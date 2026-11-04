package com.example.raglab.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import org.springframework.stereotype.Service;

/** Checks whether the configured Elasticsearch cluster is reachable. */
@Service
public class ElasticsearchHealthService {

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchHealthService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * Pings Elasticsearch without reading or changing cluster data.
     *
     * @return the current Elasticsearch connection status
     */
    public ElasticsearchHealth health() {
        try {
            if (elasticsearchClient.ping().value()) {
                return ElasticsearchHealth.up();
            }
            return ElasticsearchHealth.down("Elasticsearch did not acknowledge the ping");
        } catch (IOException | RuntimeException exception) {
            return ElasticsearchHealth.down(safeMessage(exception));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    /** Health result returned by the application API. */
    public record ElasticsearchHealth(String status, String message) {

        public static ElasticsearchHealth up() {
            return new ElasticsearchHealth("UP", null);
        }

        public static ElasticsearchHealth down(String message) {
            return new ElasticsearchHealth("DOWN", message);
        }

        @JsonIgnore
        public boolean isUp() {
            return "UP".equals(status);
        }
    }
}
