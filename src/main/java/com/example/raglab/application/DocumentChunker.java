package com.example.raglab.application;

import com.example.raglab.domain.ChunkingOptions;
import com.example.raglab.domain.DocumentChunk;
import java.util.List;

/** Strategy for splitting normalized document text into traceable chunks. */
public interface DocumentChunker {

    /**
     * Splits normalized text according to the supplied options.
     *
     * @param text normalized document text
     * @param options validated chunking options
     * @return ordered, immutable chunks
     */
    List<DocumentChunk> chunk(String text, ChunkingOptions options);
}
