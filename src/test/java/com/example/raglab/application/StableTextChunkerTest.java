package com.example.raglab.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.raglab.domain.ChunkingOptions;
import org.junit.jupiter.api.Test;

class StableTextChunkerTest {

    private final StableTextChunker chunker = new StableTextChunker();

    /** Verifies that natural paragraph boundaries are preferred over hard limits. */
    @Test
    void shouldSplitAtParagraphBoundary() {
        String text = "A".repeat(60) + "\n\n" + "B".repeat(60);

        var chunks = chunker.chunk(text, new ChunkingOptions(100, 0));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("A".repeat(60));
        assertThat(chunks.get(1).content()).isEqualTo("B".repeat(60));
        assertThat(chunks.get(1).startOffset()).isEqualTo(62);
    }

    /** Verifies that overlap remains traceable through exact source offsets. */
    @Test
    void shouldCreateDeterministicOverlap() {
        String text = "0123456789".repeat(25);

        var firstRun = chunker.chunk(text, new ChunkingOptions(100, 20));
        var secondRun = chunker.chunk(text, new ChunkingOptions(100, 20));

        assertThat(firstRun).isEqualTo(secondRun);
        assertThat(firstRun).hasSize(3);
        assertThat(firstRun.get(1).startOffset()).isEqualTo(80);
        assertThat(firstRun.get(1).content()).isEqualTo(text.substring(80, 180));
    }
}
