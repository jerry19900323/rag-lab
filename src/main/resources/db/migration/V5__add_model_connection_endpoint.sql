ALTER TABLE rag_model_connections
    ADD COLUMN account_id VARCHAR(200) NULL AFTER provider,
    ADD COLUMN api_base_url VARCHAR(500) NULL AFTER account_id;
