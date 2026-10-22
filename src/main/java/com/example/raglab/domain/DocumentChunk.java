package com.example.raglab.domain;

/** A traceable section of normalized document text. */
public record DocumentChunk(
        int index,
        int startOffset,
        int endOffset,
        int characterCount,
        String content) {

    /**
     * Creates a chunk whose offsets use the half-open interval
     * {@code [startOffset, endOffset)}.
     */
    public DocumentChunk {
        if (index < 0) {
            throw new IllegalArgumentException("Chunk index must not be negative");
        }
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Chunk offsets are invalid");
        }
        if (characterCount != endOffset - startOffset) {
            throw new IllegalArgumentException("Character count must match the chunk offsets");
        }
        if (content == null || content.length() != characterCount) {
            throw new IllegalArgumentException("Chunk content must match its character count");
        }
    }
}
