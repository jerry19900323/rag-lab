package com.example.raglab.infrastructure.jdbc;

import com.example.raglab.application.DocumentIngestionMetadata;
import com.example.raglab.application.DocumentMetadataStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores document and ingestion lifecycle metadata in MySQL. */
@Component
public class JdbcDocumentMetadataStore implements DocumentMetadataStore {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentMetadataStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void begin(DocumentIngestionMetadata metadata) {
        Timestamp startedAt = Timestamp.from(metadata.startedAt());
        jdbcTemplate.update("""
                INSERT INTO rag_documents (
                    document_id, file_name, document_type, document_hash,
                    character_count, chunk_count, current_ingestion_id,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, 'PROCESSING', ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = 'PROCESSING', updated_at = VALUES(updated_at)
                """,
                metadata.documentId(), metadata.fileName(), metadata.documentType(),
                metadata.documentHash(), metadata.characterCount(), metadata.chunkCount(),
                startedAt, startedAt);
        jdbcTemplate.update("""
                INSERT INTO rag_ingestions (
                    ingestion_id, document_id, document_hash, status,
                    character_count, chunk_count, started_at
                ) VALUES (?, ?, ?, 'PROCESSING', ?, ?, ?)
                """,
                metadata.ingestionId(), metadata.documentId(), metadata.documentHash(),
                metadata.characterCount(), metadata.chunkCount(), startedAt);
    }

    @Override
    @Transactional
    public void complete(DocumentIngestionMetadata metadata, Instant completedAt) {
        Timestamp completion = Timestamp.from(completedAt);
        jdbcTemplate.update("""
                UPDATE rag_ingestions
                SET status = 'COMPLETED', completed_at = ?, error_message = NULL
                WHERE ingestion_id = ?
                """, completion, metadata.ingestionId());
        jdbcTemplate.update("""
                UPDATE rag_documents
                SET file_name = ?, document_type = ?, document_hash = ?,
                    character_count = ?, chunk_count = ?, current_ingestion_id = ?,
                    status = 'COMPLETED', updated_at = ?
                WHERE document_id = ?
                """,
                metadata.fileName(), metadata.documentType(), metadata.documentHash(),
                metadata.characterCount(), metadata.chunkCount(), metadata.ingestionId(),
                completion, metadata.documentId());
    }

    @Override
    @Transactional
    public void fail(DocumentIngestionMetadata metadata, Instant failedAt, String errorMessage) {
        Timestamp failure = Timestamp.from(failedAt);
        jdbcTemplate.update("""
                UPDATE rag_ingestions
                SET status = 'FAILED', completed_at = ?, error_message = ?
                WHERE ingestion_id = ?
                """, failure, truncate(errorMessage), metadata.ingestionId());
        jdbcTemplate.update("""
                UPDATE rag_documents
                SET status = CASE
                        WHEN current_ingestion_id IS NULL THEN 'FAILED'
                        ELSE 'COMPLETED'
                    END,
                    updated_at = ?
                WHERE document_id = ?
                """, failure, metadata.documentId());
    }

    @Override
    @Transactional
    public void delete(String documentId) {
        jdbcTemplate.update("DELETE FROM rag_documents WHERE document_id = ?", documentId);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown indexing failure";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
