package com.shrirang.distributed_promptforge.intelligence_service.service;

/**
 * Distributed rate limiting service backed by Redis.
 * Replaces the broken in-process ConcurrentHashMap approach so rate limits
 * are enforced correctly across every pod/container.
 */
public interface RateLimitService {

    /**
     * Returns true if the user is currently blocked by a per-user cooldown.
     */
    boolean isUserInCooldown(Long userId);

    /**
     * Sets a per-user cooldown that expires after {@code cooldownMs} milliseconds.
     */
    void setUserCooldown(Long userId, long cooldownMs);

    /**
     * Returns true if the minimum inter-request gap has not elapsed yet.
     */
    boolean isTooFrequent(Long userId);

    /**
     * Records the current timestamp as the user's last request time.
     */
    void markLastRequest(Long userId);

    /**
     * Returns true if a global (service-wide) cooldown is active.
     */
    boolean isGlobalCooldownActive();

    /**
     * Sets a global cooldown that expires after {@code cooldownMs} milliseconds.
     */
    void setGlobalCooldown(long cooldownMs);
}
