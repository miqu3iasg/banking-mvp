package com.miqu3iasg.banking.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public TokenBlacklistService tokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        return new TokenBlacklistService(redisTemplate);
    }

    public static class TokenBlacklistService {

        private static final String BLACKLIST_PREFIX = "auth:token:blacklist:";
        private final RedisTemplate<String, String> redisTemplate;

        public TokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public void blacklistToken(String jti, Duration ttl) {
            try {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(TokenBlacklistService.class)
                        .warn("Redis unavailable during token blacklist write: {}", e.getMessage());
            }
        }

        public boolean isBlacklisted(String jti) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(TokenBlacklistService.class)
                        .warn("Redis unavailable during token blacklist check, allowing request: {}", e.getMessage());
                return false;
            }
        }
    }
}
