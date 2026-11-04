package com.example.raglab.application;

/** Successful login response containing an opaque Redis-backed bearer token. */
public record LoginResult(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfile user) {

    public record UserProfile(long userId, String username, String displayName, String role) {
        public UserProfile(long userId, String username, String displayName) {
            this(userId, username, displayName, "USER");
        }
    }
}
