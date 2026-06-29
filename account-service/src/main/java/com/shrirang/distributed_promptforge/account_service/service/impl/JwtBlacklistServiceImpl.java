package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.service.JwtBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Stores invalidated JWT tokens in Redis until their natural expiry.
 *
 * Key  : jwt:blacklist:{token}
 * Value: "1"
 * TTL  : remaining seconds until JWT expiration (minimum 1 s)
 */
@Service
@Slf4j
public class JwtBlacklistServiceImpl implements JwtBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;
    private final SecretKey secretKey;

    public JwtBlacklistServiceImpl(
            RedisTemplate<String, String> redisTemplate,
            @Value("${jwt.secret}") String jwtSecret) {
        this.redisTemplate = redisTemplate;
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void blacklist(String token) {
        long ttlMs = resolveRemainingTtlMs(token);
        if (ttlMs <= 0) {
            // Token already expired — nothing to blacklist
            return;
        }
        redisTemplate.opsForValue().set(PREFIX + token, "true", Duration.ofMillis(ttlMs));
        log.info("JWT blacklisted: ttlMs={}", ttlMs);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "jwt:blacklist", key = "#token", unless = "#result == false")
    public boolean isBlacklisted(String token) {
        // By default, if the token is not cached in Redis, we assume it is not blacklisted (false).
        // Since unless = "#result == false", false values are not stored in Redis.
        return false;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private long resolveRemainingTtlMs(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            if (expiration == null) return 0;
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("Could not parse JWT for blacklist TTL: {}", e.getMessage());
            return 0;
        }
    }
}
