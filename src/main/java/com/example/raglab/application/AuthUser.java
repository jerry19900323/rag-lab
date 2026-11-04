package com.example.raglab.application;

/** User credentials and profile loaded from the relational store. */
public record AuthUser(
        long userId,
        String username,
        String passwordHash,
        String displayName,
        String status,
        String role) {

    public AuthUser(long userId, String username, String passwordHash, String displayName, String status) {
        this(userId, username, passwordHash, displayName, status, "USER");
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
