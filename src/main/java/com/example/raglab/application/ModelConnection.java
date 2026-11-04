package com.example.raglab.application;

import java.time.Instant;

/** Stored model provider connection; encrypted credentials never enter API responses. */
public record ModelConnection(
        String connectionId, String name, ModelProvider provider, String encryptedApiKey,
        boolean enabled, Instant createdAt, Instant updatedAt) {

    public ModelConnectionView view() {
        return new ModelConnectionView(connectionId, name, provider, enabled,
                encryptedApiKey != null, createdAt, updatedAt);
    }
}
