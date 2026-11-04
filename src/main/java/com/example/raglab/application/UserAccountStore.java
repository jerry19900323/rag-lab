package com.example.raglab.application;

import java.util.Optional;

/** Persistence contract for login accounts. */
public interface UserAccountStore {

    Optional<AuthUser> findByUsername(String username);

    Optional<AuthUser> findById(long userId);

    boolean createIfAbsent(String username, String passwordHash, String displayName);

    void promoteToAdmin(String username);
}
