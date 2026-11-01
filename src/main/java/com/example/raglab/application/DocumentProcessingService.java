package com.example.raglab.application;

import com.example.raglab.domain.ChunkingOptions;
import com.example.raglab.domain.DocumentChunk;
import com.example.raglab.domain.SourceReference;
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
        return preview(parser.parse(file), maxCharacters, overlapCharacters);
    }

    /** Chunks already parsed content, preserving its source references. */
    public DocumentProcessingResult preview(
            ParsedDocument document,
            int maxCharacters,
            int overlapCharacters) {
        ChunkingOptions options = new ChunkingOptions(maxCharacters, overlapCharacters);
        var chunks = chunker.chunk(document.content(), options).stream()
                .map(chunk -> withSourceReferences(chunk, document))
                .toList();
        return new DocumentProcessingResult(
                document.fileName(),
                document.documentType(),
                document.content().length(),
                chunks.size(),
                options.maxCharacters(),
                options.overlapCharacters(),
                chunks);
    }

    private DocumentChunk withSourceReferences(DocumentChunk chunk, ParsedDocument document) {
        var references = document.sourceSegments().stream()
                .filter(segment -> segment.startOffset() < chunk.endOffset()
                        && segment.endOffset() > chunk.startOffset())
                .map(segment -> new SourceReference(
                        segment.pageNumber(), segment.slideNumber(), segment.sheetName(),
                        segment.sectionTitle(), segment.elementType(), segment.sourceUrl(),
                        segment.sourceTitle(), segment.webPageNumber()))
                .distinct()
                .toList();
        return new DocumentChunk(
                chunk.index(), chunk.startOffset(), chunk.endOffset(), chunk.characterCount(),
                chunk.content(), references);
    }
}
