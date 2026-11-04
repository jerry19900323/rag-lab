package com.example.raglab.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Checks the relational database and Redis dependencies used by the application. */
@Service
public class InfrastructureHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public InfrastructureHealthService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public InfrastructureHealth health() {
        ComponentHealth mysql = checkMysql();
        ComponentHealth redis = checkRedis();
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("mysql", mysql);
        components.put("redis", redis);
        String status = mysql.isUp() && redis.isUp() ? "UP" : "DOWN";
        return new InfrastructureHealth(status, Map.copyOf(components));
    }

    private ComponentHealth checkMysql() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1
                    ? ComponentHealth.up()
                    : ComponentHealth.down("Unexpected database response");
        } catch (RuntimeException exception) {
            return ComponentHealth.down(safeMessage(exception));
        }
    }

    private ComponentHealth checkRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String response = connection.ping();
            return "PONG".equalsIgnoreCase(response)
                    ? ComponentHealth.up()
                    : ComponentHealth.down("Unexpected Redis response");
        } catch (RuntimeException exception) {
            return ComponentHealth.down(safeMessage(exception));
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    public record InfrastructureHealth(String status, Map<String, ComponentHealth> components) {
        @JsonIgnore
        public boolean isUp() {
            return "UP".equals(status);
        }
    }

    public record ComponentHealth(String status, String message) {
        public static ComponentHealth up() {
            return new ComponentHealth("UP", null);
        }

        public static ComponentHealth down(String message) {
            return new ComponentHealth("DOWN", message);
        }

        @JsonIgnore
        public boolean isUp() {
            return "UP".equals(status);
        }
    }
}
