package com.example.raglab.application;

import java.time.Instant;

/** Safe connection fields shown on the administrator page. */
public record ModelConnectionView(
        String connectionId, String name, ModelProvider provider, boolean enabled,
        boolean hasApiKey, Instant createdAt, Instant updatedAt) {
}
