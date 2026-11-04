package com.example.raglab.infrastructure.jdbc;

import com.example.raglab.application.DocumentMetadataQuery;
import com.example.raglab.application.DocumentSummary;
import com.example.raglab.application.IngestionSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC read-side implementation for document and ingestion metadata. */
@Component
public class JdbcDocumentMetadataQuery implements DocumentMetadataQuery {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentMetadataQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentSummary> findDocuments(int page, int size, String document) {
        if (document == null) {
            return findAllDocuments(page, size);
        }
        return jdbcTemplate.query("""
                SELECT document_id, file_name, document_type, document_hash,
                       character_count, chunk_count, current_ingestion_id,
                       status, created_at, updated_at
                FROM rag_documents
                WHERE LOCATE(LOWER(?), LOWER(file_name)) > 0
                ORDER BY updated_at DESC, document_id ASC
                LIMIT ? OFFSET ?
                """, this::mapDocument, document, size, page * size);
    }

    @Override
    public long countDocuments(String document) {
        Long count = document == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_documents", Long.class)
                : jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM rag_documents
                        WHERE LOCATE(LOWER(?), LOWER(file_name)) > 0
                        """, Long.class, document);
        return count == null ? 0 : count;
    }

    private List<DocumentSummary> findAllDocuments(int page, int size) {
        return jdbcTemplate.query("""
                SELECT document_id, file_name, document_type, document_hash,
                       character_count, chunk_count, current_ingestion_id,
                       status, created_at, updated_at
                FROM rag_documents
                ORDER BY updated_at DESC, document_id ASC
                LIMIT ? OFFSET ?
                """, this::mapDocument, size, page * size);
    }

    @Override
    public Optional<DocumentSummary> findDocument(String documentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT document_id, file_name, document_type, document_hash,
                           character_count, chunk_count, current_ingestion_id,
                           status, created_at, updated_at
                    FROM rag_documents
                    WHERE document_id = ?
                    """, this::mapDocument, documentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<IngestionSummary> findIngestions(String documentId, int page, int size) {
        return jdbcTemplate.query("""
                SELECT ingestion_id, document_id, document_hash, status,
                       character_count, chunk_count, error_message,
                       started_at, completed_at
                FROM rag_ingestions
                WHERE document_id = ?
                ORDER BY started_at DESC, ingestion_id DESC
                LIMIT ? OFFSET ?
                """, this::mapIngestion, documentId, size, page * size);
    }

    @Override
    public long countIngestions(String documentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_ingestions WHERE document_id = ?",
                Long.class,
                documentId);
        return count == null ? 0 : count;
    }

    private DocumentSummary mapDocument(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentSummary(
                resultSet.getString("document_id"),
                resultSet.getString("file_name"),
                resultSet.getString("document_type"),
                resultSet.getString("document_hash"),
                resultSet.getInt("character_count"),
                resultSet.getInt("chunk_count"),
                resultSet.getString("current_ingestion_id"),
                resultSet.getString("status"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private IngestionSummary mapIngestion(ResultSet resultSet, int rowNumber) throws SQLException {
        return new IngestionSummary(
                resultSet.getString("ingestion_id"),
                resultSet.getString("document_id"),
                resultSet.getString("document_hash"),
                resultSet.getString("status"),
                resultSet.getInt("character_count"),
                resultSet.getInt("chunk_count"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("completed_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
