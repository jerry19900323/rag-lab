package com.example.raglab.application;

import java.util.List;
import java.util.Optional;

/** Read-side contract for relational document metadata. */
public interface DocumentMetadataQuery {

    List<DocumentSummary> findDocuments(int page, int size, String document);

    long countDocuments(String document);

    Optional<DocumentSummary> findDocument(String documentId);

    List<IngestionSummary> findIngestions(String documentId, int page, int size);

    long countIngestions(String documentId);
}
