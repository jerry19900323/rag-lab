package com.example.raglab.infrastructure.jdbc;

import com.example.raglab.application.AuthUser;
import com.example.raglab.application.UserAccountStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** MySQL-backed user account persistence. */
@Component
public class JdbcUserAccountStore implements UserAccountStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthUser> findByUsername(String username) {
        return findOne("username", username);
    }

    @Override
    public Optional<AuthUser> findById(long userId) {
        return findOne("user_id", userId);
    }

    private Optional<AuthUser> findOne(String column, Object value) {
        try {
            String sql = """
                    SELECT user_id, username, password_hash, display_name, status, role
                    FROM rag_users
                    WHERE %s = ?
                    """.formatted(column);
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new AuthUser(
                            resultSet.getLong("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("display_name"),
                            resultSet.getString("status"),
                            resultSet.getString("role")), value));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean createIfAbsent(String username, String passwordHash, String displayName) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update("""
                INSERT IGNORE INTO rag_users (
                    username, password_hash, display_name, status, role, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACTIVE', 'ADMIN', ?, ?)
                """, username, passwordHash, displayName, now, now) == 1;
    }

    @Override
    public void promoteToAdmin(String username) {
        jdbcTemplate.update("UPDATE rag_users SET role = 'ADMIN', updated_at = ? WHERE username = ? AND role <> 'ADMIN'",
                Timestamp.from(Instant.now()), username);
    }
}
