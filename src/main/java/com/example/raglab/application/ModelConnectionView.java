package com.example.raglab.application;

import java.time.Instant;

/** Connection fields returned by model settings APIs. */
public record ModelConnectionView(
        String connectionId, String name, ModelProvider provider, boolean enabled,
        boolean hasApiKey, Instant createdAt, Instant updatedAt, String accountId, String apiBaseUrl,
        String apiKey) {
    public ModelConnectionView(String connectionId, String name, ModelProvider provider, boolean enabled,
            boolean hasApiKey, Instant createdAt, Instant updatedAt, String accountId, String apiBaseUrl) {
        this(connectionId, name, provider, enabled, hasApiKey, createdAt, updatedAt,
                accountId, apiBaseUrl, null);
    }

    public ModelConnectionView(String connectionId, String name, ModelProvider provider, boolean enabled,
            boolean hasApiKey, Instant createdAt, Instant updatedAt) {
        this(connectionId, name, provider, enabled, hasApiKey, createdAt, updatedAt, null, null, null);
    }

    @Override
    public String toString() {
        return "ModelConnectionView[connectionId=" + connectionId + ", apiKey=[REDACTED]]";
    }
}
