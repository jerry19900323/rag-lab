package com.example.raglab.domain;

/** Validated controls for deterministic text chunking. */
public record ChunkingOptions(int maxCharacters, int overlapCharacters) {

    public static final int DEFAULT_MAX_CHARACTERS = 800;
    public static final int DEFAULT_OVERLAP_CHARACTERS = 100;
    public static final int MIN_MAX_CHARACTERS = 100;
    public static final int MAX_MAX_CHARACTERS = 10_000;

    /** Validates limits that protect both chunk quality and service resources. */
    public ChunkingOptions {
        if (maxCharacters < MIN_MAX_CHARACTERS || maxCharacters > MAX_MAX_CHARACTERS) {
            throw new IllegalArgumentException("maxCharacters must be between 100 and 10000");
        }
        if (overlapCharacters < 0 || overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("overlapCharacters must be non-negative and smaller than maxCharacters");
        }
    }
}
