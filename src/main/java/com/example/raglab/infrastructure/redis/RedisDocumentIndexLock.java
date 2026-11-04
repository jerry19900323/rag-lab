package com.example.raglab.infrastructure.redis;

import com.example.raglab.application.DocumentIndexLock;
import com.example.raglab.application.DocumentIndexingConflictException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis lock with ownership-safe release and a bounded lifetime. */
@Component
public class RedisDocumentIndexLock implements DocumentIndexLock {

    private static final String KEY_PREFIX = "rag:indexing:lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;

    public RedisDocumentIndexLock(
            StringRedisTemplate redisTemplate,
            @Value("${rag.indexing.lock-ttl:5m}") Duration lockTtl) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = lockTtl;
    }

    @Override
    public LockHandle acquire(String documentId) {
        String key = KEY_PREFIX + documentId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new DocumentIndexingConflictException(documentId);
        }
        return () -> redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }
}
