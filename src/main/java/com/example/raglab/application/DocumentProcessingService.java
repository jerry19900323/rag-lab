package com.example.raglab.application;

import com.example.raglab.domain.ChunkingOptions;
import com.example.raglab.infrastructure.document.DocumentParser;
import com.example.raglab.infrastructure.document.ParsedDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates document validation, parsing, normalization, and chunking. */
@Service
public class DocumentProcessingService {

    private final DocumentParser parser;
    private final DocumentChunker chunker;

    public DocumentProcessingService(DocumentParser parser, DocumentChunker chunker) {
        this.parser = parser;
        this.chunker = chunker;
    }

    /** Processes one uploaded document without persisting its content. */
    public DocumentProcessingResult preview(
            MultipartFile file,
            int maxCharacters,
            int overlapCharacters) {
        ChunkingOptions options = new ChunkingOptions(maxCharacters, overlapCharacters);
        ParsedDocument document = parser.parse(file);
        var chunks = chunker.chunk(document.content(), options);
        return new DocumentProcessingResult(
                document.fileName(),
                document.documentType(),
                document.content().length(),
                chunks.size(),
                options.maxCharacters(),
                options.overlapCharacters(),
                chunks);
    }
}
