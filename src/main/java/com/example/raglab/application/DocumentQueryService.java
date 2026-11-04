package com.example.raglab.application;

import org.springframework.stereotype.Service;

/** Provides validated, paginated document metadata queries for API consumers. */
@Service
public class DocumentQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentMetadataQuery metadataQuery;

    public DocumentQueryService(DocumentMetadataQuery metadataQuery) {
        this.metadataQuery = metadataQuery;
    }

    public PagedResult<DocumentSummary> listDocuments(int page, int size, String document) {
        validatePage(page, size);
        String keyword = normalizeKeyword(document);
        return PagedResult.of(
                metadataQuery.findDocuments(page, size, keyword),
                page,
                size,
                metadataQuery.countDocuments(keyword));
    }

    public DocumentSummary getDocument(String documentId) {
        validateDocumentId(documentId);
        return metadataQuery.findDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public PagedResult<IngestionSummary> listIngestions(String documentId, int page, int size) {
        getDocument(documentId);
        validatePage(page, size);
        return PagedResult.of(
                metadataQuery.findIngestions(documentId, page, size),
                page,
                size,
                metadataQuery.countIngestions(documentId));
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private void validateDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId is required");
        }
    }

    private String normalizeKeyword(String document) {
        if (document == null || document.isBlank()) {
            return null;
        }
        return document.strip();
    }
}
