package com.example.raglab.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Stores bounded execution diagnostics for each active conversation. */
@Service
public class ChatTraceService {
    private static final String PREFIX = "rag:chat:traces:";
    private final StringRedisTemplate redis;
    private final ConversationHistoryService history;
    private final Duration ttl;
    private final JsonMapper json = new JsonMapper();

    public ChatTraceService(StringRedisTemplate redis, ConversationHistoryService history,
            @Value("${rag.chat.history-ttl:24h}") Duration ttl) {
        this.redis = redis;
        this.history = history;
        this.ttl = ttl;
    }

    public void save(long userId, String conversationId, Trace trace) {
        String key = key(userId, conversationId);
        redis.opsForList().rightPush(key, json.writeValueAsString(trace));
        redis.opsForList().trim(key, -100, -1);
        redis.expire(key, ttl);
    }

    public PagedResult<Trace> page(long userId, String conversationId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be 1 to 100");
        history.totalTurns(userId, conversationId);
        Long length = redis.opsForList().size(key(userId, conversationId));
        long total = length == null ? 0 : length;
        long offset = (long) page * size;
        if (offset >= total) return PagedResult.of(List.of(), page, size, total);
        long end = total - 1 - offset;
        long start = Math.max(0, end - size + 1);
        List<String> values = redis.opsForList().range(key(userId, conversationId), start, end);
        if (values == null) return PagedResult.of(List.of(), page, size, total);
        List<String> descending = new java.util.ArrayList<>(values);
        java.util.Collections.reverse(descending);
        List<Trace> items = descending.stream()
                .map(value -> json.readValue(value, Trace.class)).toList();
        return PagedResult.of(items, page, size, total);
    }

    public void delete(long userId, String conversationId) {
        redis.delete(key(userId, conversationId));
    }

    private String key(long userId, String conversationId) {
        return PREFIX + userId + ":" + (conversationId == null ? "legacy" : conversationId);
    }

    public record Trace(String requestId, String question, String status, String startedAt,
            String finishedAt, List<Map<String, Object>> steps, String error) {
        public Trace { steps = List.copyOf(steps); }
    }
}
