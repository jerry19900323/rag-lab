ALTER TABLE rag_users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
UPDATE rag_users SET role = 'ADMIN' WHERE username = 'admin';

CREATE TABLE rag_model_connections (
    connection_id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    api_key_encrypted TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rag_model_assignments (
    purpose VARCHAR(32) NOT NULL PRIMARY KEY,
    connection_id CHAR(36) NULL,
    model_id VARCHAR(200) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_rag_model_assignments_connection FOREIGN KEY (connection_id)
        REFERENCES rag_model_connections (connection_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
