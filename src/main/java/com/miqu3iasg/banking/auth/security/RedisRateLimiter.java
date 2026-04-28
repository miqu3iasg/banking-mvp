package com.miqu3iasg.banking.auth.security;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Component
public class RedisRateLimiter {

    private static final String KEY_GENERAL = "ratelimit:ip:%s:general";
    private static final String KEY_LOGIN = "ratelimit:ip:%s:login";
    private static final String KEY_PASSWORD_RESET = "ratelimit:ip:%s:password_reset";
    private static final Duration KEY_TTL = Duration.ofHours(2);

    private static final String RATE_LIMIT_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local ttl = tonumber(ARGV[2])
        local current = tonumber(redis.call('GET', key) or '0')
        if current < limit then
            redis.call('INCR', key)
            if current == 0 then
                redis.call('EXPIRE', key, ttl)
            end
            return 1
        end
        return 0
        """;

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final boolean failOpen;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, AuthProperties authProperties) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.failOpen = authProperties.getRedis().isFailOpen();
        this.rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    }

    public boolean tryConsumeGeneral(String ipHash) {
        return tryConsume(
            String.format(KEY_GENERAL, ipHash),
            authProperties.getRateLimit().getRequestsPerMinute(),
            (int) Duration.ofMinutes(1).getSeconds()
        );
    }

    public boolean tryConsumeLogin(String ipHash) {
        return tryConsume(
            String.format(KEY_LOGIN, ipHash),
            authProperties.getRateLimit().getLoginRequestsPerMinute(),
            (int) Duration.ofMinutes(1).getSeconds()
        );
    }

    public boolean tryConsumePasswordReset(String ipHash) {
        return tryConsume(
            String.format(KEY_PASSWORD_RESET, ipHash),
            authProperties.getRateLimit().getPasswordResetPerHour(),
            (int) Duration.ofHours(1).getSeconds()
        );
    }

    private boolean tryConsume(String key, int limit, int ttlSeconds) {
        try {
            Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(ttlSeconds)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            if (failOpen) {
                log.warn("Redis unavailable for rate limiting — failing open: {}", e.getMessage());
                return true;
            }
            log.error("Redis unavailable for rate limiting — failing closed");
            return false;
        }
    }

    public void reset(String ipHash) {
        try {
            redisTemplate.delete(String.format(KEY_GENERAL, ipHash));
            redisTemplate.delete(String.format(KEY_LOGIN, ipHash));
            redisTemplate.delete(String.format(KEY_PASSWORD_RESET, ipHash));
        } catch (Exception e) {
            log.warn("Failed to reset rate limit counters for {}: {}", ipHash, e.getMessage());
        }
    }
}
