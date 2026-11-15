package com.example.raglab.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Stores a bounded conversation history for each authenticated user. */
@Service
public class ConversationHistoryService {
    private static final String KEY_PREFIX = "rag:chat:history:";
    private static final String ARCHIVE_PREFIX = "rag:chat:archive:";
    private static final String CONVERSATION_PREFIX = "rag:chat:conversation:";
    private static final String INDEX_PREFIX = "rag:chat:conversation-index:";
    private final StringRedisTemplate redis;
    private final JsonMapper json = new JsonMapper();
    private final int maxTurns;
    private final Duration ttl;

    public ConversationHistoryService(StringRedisTemplate redis,
            @Value("${rag.chat.history-max-turns:10}") int maxTurns,
            @Value("${rag.chat.history-ttl:24h}") Duration ttl) {
        if (maxTurns < 1 || maxTurns > 100) throw new IllegalArgumentException("history-max-turns must be 1 to 100");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("history-ttl must be positive");
        this.redis = redis;
        this.maxTurns = maxTurns;
        this.ttl = ttl;
    }

    public List<Turn> recent(long userId) {
        return recent(userId, null);
    }

    public List<Turn> recent(long userId, String conversationId) {
        validate(userId, conversationId);
        List<String> values = redis.opsForList().range(key(userId, conversationId), -maxTurns, -1);
        return values == null ? List.of() : values.stream()
                .map(value -> json.readValue(value, Turn.class)).toList();
    }

    public void append(long userId, String question, String answer) {
        append(userId, null, question, answer);
    }

    public void append(long userId, String conversationId, String question, String answer) {
        validate(userId, conversationId);
        String key = key(userId, conversationId);
        String value = json.writeValueAsString(new Turn(question, answer));
        String archive = archiveKey(userId, conversationId);
        Long archiveSize = redis.opsForList().size(archive);
        if (archiveSize == null || archiveSize == 0) {
            List<String> retained = redis.opsForList().range(key, 0, -1);
            if (retained != null && !retained.isEmpty()) redis.opsForList().rightPushAll(archive, retained);
        }
        redis.opsForList().rightPush(key, value);
        redis.opsForList().trim(key, -maxTurns, -1);
        redis.expire(key, ttl);
        redis.opsForList().rightPush(archive, value);
        redis.expire(archive, ttl);
        if (conversationId != null) {
            redis.opsForValue().set(metaKey(userId, conversationId), Instant.now().toString(), ttl);
            redis.expire(INDEX_PREFIX + userId, ttl);
        }
    }

    public ConversationView create(long userId) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        redis.opsForValue().set(metaKey(userId, id), now, ttl);
        redis.opsForSet().add(INDEX_PREFIX + userId, id);
        redis.expire(INDEX_PREFIX + userId, ttl);
        return new ConversationView(id, now);
    }

    public List<ConversationView> list(long userId) {
        var ids = redis.opsForSet().members(INDEX_PREFIX + userId);
        if (ids == null) return List.of();
        return ids.stream().map(id -> new ConversationView(id, redis.opsForValue().get(metaKey(userId, id))))
                .filter(view -> view.updatedAt() != null)
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .toList();
    }

    public void delete(long userId, String conversationId) {
        validate(userId, conversationId);
        redis.delete(key(userId, conversationId));
        redis.delete(archiveKey(userId, conversationId));
        redis.delete(metaKey(userId, conversationId));
        redis.opsForSet().remove(INDEX_PREFIX + userId, conversationId);
    }

    private void validate(long userId, String conversationId) {
        if (conversationId != null && !Boolean.TRUE.equals(redis.hasKey(metaKey(userId, conversationId)))) {
            throw new IllegalArgumentException("conversationId is not found for the current user");
        }
    }

    private String key(long userId, String conversationId) {
        return KEY_PREFIX + userId + (conversationId == null ? "" : ":" + conversationId);
    }

    public long totalTurns(long userId, String conversationId) {
        validate(userId, conversationId);
        Long archiveSize = redis.opsForList().size(archiveKey(userId, conversationId));
        if (archiveSize != null && archiveSize > 0) return archiveSize;
        Long recentSize = redis.opsForList().size(key(userId, conversationId));
        return recentSize == null ? 0 : recentSize;
    }

    public PagedResult<Turn> page(long userId, String conversationId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be 1 to 100");
        }
        long total = totalTurns(userId, conversationId);
        String archive = archiveKey(userId, conversationId);
        Long archiveSize = redis.opsForList().size(archive);
        String source = archiveSize != null && archiveSize > 0 ? archive : key(userId, conversationId);
        long start = (long) page * size;
        if (start >= total) return PagedResult.of(List.of(), page, size, total);
        List<String> values = redis.opsForList().range(source, start, Math.min(total - 1, start + size - 1));
        List<Turn> items = values == null ? List.of() : values.stream()
                .map(value -> json.readValue(value, Turn.class)).toList();
        return PagedResult.of(items, page, size, total);
    }

    private String archiveKey(long userId, String conversationId) {
        return ARCHIVE_PREFIX + userId + (conversationId == null ? "" : ":" + conversationId);
    }

    private String metaKey(long userId, String conversationId) {
        return CONVERSATION_PREFIX + userId + ":" + conversationId;
    }

    public record Turn(String question, String answer) {}
    public record ConversationView(String conversationId, String updatedAt) {}
}
