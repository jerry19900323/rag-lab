package com.example.raglab.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Issues revocable opaque login sessions backed by Redis. */
@Service
public class AuthSessionService {

    private static final String KEY_PREFIX = "rag:auth:session:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(
            StringRedisTemplate redisTemplate,
            @Value("${rag.auth.session-ttl:8h}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public Session create(AuthUser user) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, Long.toString(user.userId()), ttl);
        return new Session(token, ttl.toSeconds());
    }

    public Optional<Long> findUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            redisTemplate.delete(KEY_PREFIX + token);
            return Optional.empty();
        }
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    public record Session(String token, long expiresInSeconds) {
    }
}
