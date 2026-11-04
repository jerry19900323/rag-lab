package com.example.raglab.application;

import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkDocument;
import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkStore;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates document processing, hashing, generation identity, and durable chunk storage. */
@Service
public class DocumentIndexingService {

    private static final int MAX_DOCUMENT_ID_LENGTH = 200;

    private final DocumentProcessingService processingService;
    private final ElasticsearchChunkStore chunkStore;
    private final DocumentIdentifierGenerator identifierGenerator;
    private final Sha256HashService hashService;
    private final DocumentMetadataStore metadataStore;
    private final DocumentIndexLock indexLock;

    public DocumentIndexingService(
            DocumentProcessingService processingService,
            ElasticsearchChunkStore chunkStore,
            DocumentIdentifierGenerator identifierGenerator,
            Sha256HashService hashService,
            DocumentMetadataStore metadataStore,
            DocumentIndexLock indexLock) {
        this.processingService = processingService;
        this.chunkStore = chunkStore;
        this.identifierGenerator = identifierGenerator;
        this.hashService = hashService;
        this.metadataStore = metadataStore;
        this.indexLock = indexLock;
    }

    /** Processes and atomically replaces the indexed generation of one logical document. */
    public DocumentIndexingResult index(
            MultipartFile file,
            String requestedDocumentId,
            int maxCharacters,
            int overlapCharacters) {
        String documentHash = hashUploadedFile(file);
        DocumentProcessingResult processed = processingService.preview(
                file, maxCharacters, overlapCharacters);
        return indexProcessed(processed, requestedDocumentId, documentHash);
    }

    /** Commits already fetched and processed content through the normal ingestion lifecycle. */
    public DocumentIndexingResult indexProcessed(
            DocumentProcessingResult processed,
            String requestedDocumentId,
            String documentHash) {
        if (processed == null || documentHash == null || documentHash.isBlank()) {
            throw new IllegalArgumentException("Processed document and document hash are required");
        }
        String documentId = resolveDocumentId(requestedDocumentId);
        try (DocumentIndexLock.LockHandle ignored = indexLock.acquire(documentId)) {
            return indexWhileLocked(processed, documentId, documentHash);
        }
    }

    private DocumentIndexingResult indexWhileLocked(
            DocumentProcessingResult processed,
            String documentId,
            String documentHash) {
        String ingestionId = identifierGenerator.newIngestionId();
        Instant indexedAt = Instant.now();
        DocumentIngestionMetadata metadata = new DocumentIngestionMetadata(
                documentId, ingestionId, processed.fileName(), processed.documentType(),
                documentHash, processed.characterCount(), processed.chunkCount(), indexedAt);

        List<ElasticsearchChunkDocument> chunks = processed.chunks().stream()
                .map(chunk -> new ElasticsearchChunkDocument(
                        documentId,
                        ingestionId,
                        processed.fileName(),
                        processed.documentType(),
                        documentHash,
                        chunk.index(),
                        chunk.content(),
                        chunk.characterCount(),
                        chunk.startOffset(),
                        chunk.endOffset(),
                        chunk.sourceReferences(),
                        hashService.hashText(chunk.content()),
                        indexedAt,
                        ElasticsearchChunkDocument.SCHEMA_VERSION))
                .toList();

        metadataStore.begin(metadata);
        try {
            chunkStore.replaceDocumentChunks(documentId, ingestionId, chunks);
        } catch (RuntimeException exception) {
            recordFailure(metadata, exception);
            throw exception;
        }
        metadataStore.complete(metadata, Instant.now());
        return new DocumentIndexingResult(
                documentId,
                ingestionId,
                processed.fileName(),
                processed.documentType(),
                documentHash,
                processed.characterCount(),
                processed.chunkCount(),
                indexedAt);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private void recordFailure(DocumentIngestionMetadata metadata, RuntimeException indexingFailure) {
        try {
            metadataStore.fail(metadata, Instant.now(), safeMessage(indexingFailure));
        } catch (RuntimeException metadataFailure) {
            indexingFailure.addSuppressed(metadataFailure);
        }
    }

    private String resolveDocumentId(String requestedDocumentId) {
        if (requestedDocumentId == null || requestedDocumentId.isBlank()) {
            return identifierGenerator.newDocumentId();
        }
        String documentId = requestedDocumentId.trim();
        if (documentId.length() > MAX_DOCUMENT_ID_LENGTH) {
            throw new IllegalArgumentException("documentId must not exceed 200 characters");
        }
        return documentId;
    }

    private String hashUploadedFile(MultipartFile file) {
        try {
            return hashService.hashBytes(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("The document could not be read", exception);
        }
    }
}
