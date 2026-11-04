package com.example.raglab.application;

import com.example.raglab.infrastructure.elasticsearch.ElasticsearchChunkStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Deletes a logical document across the search index and metadata store. */
@Service
public class DocumentDeletionService {

    private final DocumentMetadataQuery metadataQuery;
    private final DocumentMetadataStore metadataStore;
    private final ElasticsearchChunkStore chunkStore;
    private final DocumentIndexLock indexLock;

    public DocumentDeletionService(
            DocumentMetadataQuery metadataQuery,
            DocumentMetadataStore metadataStore,
            ElasticsearchChunkStore chunkStore,
            DocumentIndexLock indexLock) {
        this.metadataQuery = metadataQuery;
        this.metadataStore = metadataStore;
        this.chunkStore = chunkStore;
        this.indexLock = indexLock;
    }

    public void delete(String documentId) {
        if (documentId == null || documentId.isBlank() || documentId.length() > 200) {
            throw new IllegalArgumentException("documentId must contain 1 to 200 characters");
        }
        DocumentIndexLock.LockHandle handle;
        try {
            handle = indexLock.acquire(documentId);
        } catch (DocumentIndexingConflictException exception) {
            throw new DocumentDeletionConflictException(documentId);
        }
        try (DocumentIndexLock.LockHandle ignored = handle) {
            metadataQuery.findDocument(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));
            chunkStore.deleteDocumentChunks(documentId);
            try {
                metadataStore.delete(documentId);
            } catch (DataAccessException exception) {
                throw new DocumentMetadataDeleteException(exception);
            }
        }
    }
}
