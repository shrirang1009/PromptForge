package com.shrirang.distributed_promptforge.account_service.service;

/**
 * Redis-backed JWT blacklist.
 *
 * On logout the token is stored with TTL = remaining JWT lifetime.
 * Every inbound request checks this list before proceeding.
 */
public interface JwtBlacklistService {

    /** Blacklist a token until it naturally expires. */
    void blacklist(String token);

    /** Returns true if the token has been explicitly invalidated. */
    boolean isBlacklisted(String token);
}
