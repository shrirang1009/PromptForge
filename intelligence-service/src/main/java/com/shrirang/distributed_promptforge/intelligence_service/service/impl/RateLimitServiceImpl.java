package com.shrirang.distributed_promptforge.intelligence_service.service.impl;

import com.shrirang.distributed_promptforge.intelligence_service.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed distributed rate limiter.
 *
 * Key layout:
 *   rl:user:cooldown:{userId}   → exists while user is in cooldown  (TTL = cooldown duration)
 *   rl:user:last_req:{userId}   → epoch-ms of the user's last request (TTL = MIN_GAP + buffer)
 *   rl:global:cooldown          → exists while a global cooldown is active (TTL = cooldown duration)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private static final long MIN_REQUEST_GAP_MS = 2500L;

    private static final String PREFIX_USER_COOLDOWN = "rl:user:cooldown:";
    private static final String PREFIX_USER_LAST_REQ = "rl:user:last_req:";
    private static final String GLOBAL_COOLDOWN_KEY  = "rl:global:cooldown";

    private final RedisTemplate<String, String> redisTemplate;

    // ── per-user cooldown ──────────────────────────────────────────────────────

    @Override
    public boolean isUserInCooldown(Long userId) {
        Boolean exists = redisTemplate.hasKey(PREFIX_USER_COOLDOWN + userId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void setUserCooldown(Long userId, long cooldownMs) {
        redisTemplate.opsForValue().set(
                PREFIX_USER_COOLDOWN + userId,
                "1",
                Duration.ofMillis(cooldownMs)
        );
        log.debug("User cooldown set: userId={}, cooldownMs={}", userId, cooldownMs);
    }

    // ── minimum inter-request gap ──────────────────────────────────────────────

    @Override
    public boolean isTooFrequent(Long userId) {
        String raw = redisTemplate.opsForValue().get(PREFIX_USER_LAST_REQ + userId);
        if (raw == null) return false;
        long lastAt = Long.parseLong(raw);
        return (System.currentTimeMillis() - lastAt) < MIN_REQUEST_GAP_MS;
    }

    @Override
    public void markLastRequest(Long userId) {
        // TTL slightly longer than MIN_GAP so Redis can auto-expire the key
        redisTemplate.opsForValue().set(
                PREFIX_USER_LAST_REQ + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(MIN_REQUEST_GAP_MS + 5000L)
        );
    }

    // ── global cooldown ────────────────────────────────────────────────────────

    @Override
    public boolean isGlobalCooldownActive() {
        Boolean exists = redisTemplate.hasKey(GLOBAL_COOLDOWN_KEY);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void setGlobalCooldown(long cooldownMs) {
        redisTemplate.opsForValue().set(
                GLOBAL_COOLDOWN_KEY,
                "1",
                Duration.ofMillis(cooldownMs)
        );
        log.warn("Global AI cooldown set: cooldownMs={}", cooldownMs);
    }
}
