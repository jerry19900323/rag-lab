package com.example.raglab.application;

import java.time.Instant;

/** Stored model provider connection. */
public record ModelConnection(
        String connectionId, String name, ModelProvider provider, String encryptedApiKey,
        boolean enabled, Instant createdAt, Instant updatedAt, String accountId, String apiBaseUrl) {

    public ModelConnection(String connectionId, String name, ModelProvider provider, String encryptedApiKey,
            boolean enabled, Instant createdAt, Instant updatedAt) {
        this(connectionId, name, provider, encryptedApiKey, enabled, createdAt, updatedAt, null, null);
    }

}
