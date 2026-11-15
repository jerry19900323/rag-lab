package com.example.raglab.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Calls the configured text embedding model for both indexing and search. */
@Service
public class EmbeddingService {
    private final ModelSelectionResolver resolver;
    private final RestClient client;

    public EmbeddingService(ModelSelectionResolver resolver) {
        this.resolver = resolver;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() { return resolver.resolve(ModelPurpose.EMBEDDING).isPresent(); }

    public String modelKey() {
        ResolvedModel model = resolver.resolve(ModelPurpose.EMBEDDING).orElseThrow();
        return model.provider() + ":" + (model.accountId() == null ? "" : model.accountId() + ":") + model.modelId();
    }

    public List<Float> embed(String text) {
        ResolvedModel model = resolver.resolve(ModelPurpose.EMBEDDING)
                .orElseThrow(() -> new EmbeddingException("No EMBEDDING model is configured"));
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Embedding text is required");
        if (model.provider() != ModelProvider.CLOUDFLARE && model.provider() != ModelProvider.OPENAI
                && model.provider() != ModelProvider.DASHSCOPE) {
            throw new EmbeddingException("The configured embedding provider has no inference adapter");
        }
        String base = model.apiBaseUrl();
        if (model.provider() == ModelProvider.CLOUDFLARE
                && (base == null || base.endsWith("/client/v4"))) {
            base = "https://api.cloudflare.com/client/v4/accounts/" + model.accountId() + "/ai/v1";
        } else if (base == null) {
            base = model.provider() == ModelProvider.OPENAI ? "https://api.openai.com/v1"
                    : "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
        }
        try {
            Map<?, ?> response = client.post().uri(base + "/embeddings")
                    .headers(headers -> headers.setBearerAuth(model.apiKey()))
                    .body(Map.of("model", model.modelId(), "input", text))
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()
                    || !(data.get(0) instanceof Map<?, ?> first)
                    || !(first.get("embedding") instanceof List<?> values) || values.isEmpty()) {
                throw new EmbeddingException("Embedding provider returned no vector");
            }
            List<Float> vector = values.stream().map(value -> {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
                    throw new EmbeddingException("Embedding provider returned an invalid vector");
                return number.floatValue();
            }).toList();
            return vector;
        } catch (EmbeddingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmbeddingException("Embedding provider request failed", exception);
        }
    }
}
