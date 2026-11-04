package com.example.raglab.application;

import java.time.Instant;

/** Selected model for a use case; null values mean the use case is disabled. */
public record ModelAssignment(
        ModelPurpose purpose, String connectionId, String modelId, Instant updatedAt) {
}
