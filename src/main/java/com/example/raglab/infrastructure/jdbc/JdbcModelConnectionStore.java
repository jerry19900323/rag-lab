package com.example.raglab.infrastructure.jdbc;

import com.example.raglab.application.ModelAssignment;
import com.example.raglab.application.ModelConnection;
import com.example.raglab.application.ModelConnectionStore;
import com.example.raglab.application.ModelProvider;
import com.example.raglab.application.ModelPurpose;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC persistence for administrator-managed model settings. */
@Component
public class JdbcModelConnectionStore implements ModelConnectionStore {

    private final JdbcTemplate jdbc;

    public JdbcModelConnectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ModelConnection> listConnections() {
        return jdbc.query("SELECT * FROM rag_model_connections ORDER BY created_at, connection_id", this::connection);
    }

    @Override
    public Optional<ModelConnection> findConnection(String id) {
        return jdbc.query("SELECT * FROM rag_model_connections WHERE connection_id = ?", this::connection, id)
                .stream().findFirst();
    }

    @Override
    public void saveConnection(ModelConnection connection) {
        jdbc.update("""
                INSERT INTO rag_model_connections
                    (connection_id, name, provider, api_key_encrypted, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), provider = VALUES(provider),
                    api_key_encrypted = VALUES(api_key_encrypted), enabled = VALUES(enabled),
                    updated_at = VALUES(updated_at)
                """, connection.connectionId(), connection.name(), connection.provider().name(),
                connection.encryptedApiKey(), connection.enabled(),
                Timestamp.from(connection.createdAt()), Timestamp.from(connection.updatedAt()));
    }

    @Override
    @Transactional
    public void deleteConnection(String id) {
        jdbc.update("""
                UPDATE rag_model_assignments
                SET connection_id = NULL, model_id = NULL, updated_at = ?
                WHERE connection_id = ?
                """, Timestamp.from(Instant.now()), id);
        jdbc.update("DELETE FROM rag_model_connections WHERE connection_id = ?", id);
    }

    @Override
    public List<ModelAssignment> listAssignments() {
        return jdbc.query("SELECT * FROM rag_model_assignments ORDER BY purpose", this::assignment);
    }

    @Override
    public void saveAssignment(ModelAssignment assignment) {
        jdbc.update("""
                INSERT INTO rag_model_assignments (purpose, connection_id, model_id, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE connection_id = VALUES(connection_id),
                    model_id = VALUES(model_id), updated_at = VALUES(updated_at)
                """, assignment.purpose().name(), assignment.connectionId(), assignment.modelId(),
                Timestamp.from(assignment.updatedAt()));
    }

    private ModelConnection connection(ResultSet row, int ignored) throws SQLException {
        return new ModelConnection(row.getString("connection_id"), row.getString("name"),
                ModelProvider.valueOf(row.getString("provider")), row.getString("api_key_encrypted"),
                row.getBoolean("enabled"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private ModelAssignment assignment(ResultSet row, int ignored) throws SQLException {
        return new ModelAssignment(ModelPurpose.valueOf(row.getString("purpose")),
                row.getString("connection_id"), row.getString("model_id"),
                row.getTimestamp("updated_at").toInstant());
    }
}
