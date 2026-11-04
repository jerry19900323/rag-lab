package com.example.raglab.infrastructure.web;

import com.example.raglab.application.ModelCatalogException;
import com.example.raglab.application.ModelOption;
import com.example.raglab.application.ModelProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads models from fixed provider catalog endpoints, never from a client-supplied URL. */
@Component
public class ModelCatalogClient {

    private final RestClient client;

    public ModelCatalogClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public List<ModelOption> listModels(ModelProvider provider, String apiKey) {
        try {
            RestClient.RequestHeadersSpec<?> request = client.get().uri(provider.catalogUrl());
            switch (provider) {
                case ANTHROPIC -> request.headers(headers -> {
                    headers.set("x-api-key", apiKey);
                    headers.set("anthropic-version", "2023-06-01");
                });
                case GEMINI -> request.header("x-goog-api-key", apiKey);
                case OLLAMA -> { }
                default -> request.headers(headers -> headers.setBearerAuth(apiKey));
            }
            Map<?, ?> body = request.retrieve().body(Map.class);
            if (body == null) {
                throw new IllegalStateException("Empty provider response");
            }
            return parseModels(provider, body);
        } catch (RuntimeException exception) {
            throw new ModelCatalogException("The provider model list could not be loaded", exception);
        }
    }

    List<ModelOption> parseModels(ModelProvider provider, Map<?, ?> body) {
        Object raw = body.get(provider == ModelProvider.GEMINI || provider == ModelProvider.OLLAMA
                ? "models" : "data");
        if (!(raw instanceof List<?> entries)) {
            throw new IllegalStateException("Provider did not return a model list");
        }
        return entries.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(entry -> toOption(provider, entry))
                    .filter(option -> option != null)
                    .distinct()
                    .sorted(Comparator.comparing(ModelOption::displayName))
                    .toList();
    }

    private ModelOption toOption(ModelProvider provider, Map<?, ?> entry) {
        Object rawId = entry.get(provider == ModelProvider.OLLAMA ? "name" :
                provider == ModelProvider.GEMINI ? "name" : "id");
        if (!(rawId instanceof String id) || id.isBlank()) {
            return null;
        }
        if (provider == ModelProvider.GEMINI && id.startsWith("models/")) {
            id = id.substring("models/".length());
        }
        Object rawName = entry.get("displayName");
        if (rawName == null) rawName = entry.get("name");
        String displayName = rawName instanceof String name && !name.isBlank() ? name : id;
        return new ModelOption(id, displayName);
    }
}
