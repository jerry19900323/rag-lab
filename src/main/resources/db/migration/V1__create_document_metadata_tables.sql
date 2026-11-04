CREATE TABLE rag_documents (
    document_id VARCHAR(200) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    document_hash CHAR(64) NOT NULL,
    character_count INT UNSIGNED NOT NULL,
    chunk_count INT UNSIGNED NOT NULL,
    current_ingestion_id CHAR(36) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (document_id),
    KEY idx_rag_documents_status (status),
    KEY idx_rag_documents_hash (document_hash),
    KEY idx_rag_documents_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rag_ingestions (
    ingestion_id CHAR(36) NOT NULL,
    document_id VARCHAR(200) NOT NULL,
    document_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    character_count INT UNSIGNED NOT NULL,
    chunk_count INT UNSIGNED NOT NULL,
    error_message TEXT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (ingestion_id),
    KEY idx_rag_ingestions_document_started (document_id, started_at),
    KEY idx_rag_ingestions_status (status),
    CONSTRAINT fk_rag_ingestions_document
        FOREIGN KEY (document_id) REFERENCES rag_documents (document_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
