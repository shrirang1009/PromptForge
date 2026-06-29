package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.service.OtpCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Annotation-based Redis OTP cache — fully Spring-proxy-safe.
 *
 * Problem with self-invocation:
 *   Spring AOP annotations (@CachePut, @Cacheable, @CacheEvict) only fire
 *   when called through the Spring proxy, NOT via `this.method()`.
 *   So save/get/delete for the same cache must be on DIFFERENT beans,
 *   or the @CachePut store and @Cacheable read must each be a direct proxy call.
 *
 * Solution used here:
 *   OtpCacheServiceImpl is the public facade (injected everywhere).
 *   OtpStore is an internal Spring bean whose every method is a direct
 *   proxy call — no self-invocation, all annotations fire correctly.
 *
 * Cache names → TTLs (configured in RedisConfig):
 *   "otp:signup"   → 15 minutes
 *   "otp:verified" → 10 minutes
 *   "otp:forgot"   → 15 minutes
 */
@Service
@RequiredArgsConstructor
public class OtpCacheServiceImpl implements OtpCacheService {

    private final OtpStore otpStore;

    // ── Signup OTP ───────────────────────────────────────────────────────────

    @Override
    public void saveSignupOtp(String email, String otp) {
        otpStore.putSignupOtp(email, otp);
    }

    @Override
    public String getSignupOtp(String email) {
        return otpStore.fetchSignupOtp(email);
    }

    @Override
    public void deleteSignupOtp(String email) {
        otpStore.evictSignupOtp(email);
    }

    // ── Signup Verified flag ─────────────────────────────────────────────────

    @Override
    public void saveSignupVerified(String email) {
        otpStore.putSignupVerified(email);
    }

    @Override
    public String getSignupVerified(String email) {
        return otpStore.fetchSignupVerified(email);
    }

    @Override
    public void deleteSignupVerified(String email) {
        otpStore.evictSignupVerified(email);
    }

    // ── Forgot Password OTP ──────────────────────────────────────────────────

    @Override
    public void saveForgotOtp(String email, String otp) {
        otpStore.putForgotOtp(email, otp);
    }

    @Override
    public String getForgotOtp(String email) {
        return otpStore.fetchForgotOtp(email);
    }

    @Override
    public void deleteForgotOtp(String email) {
        otpStore.evictForgotOtp(email);
    }

    // ── Inner store bean (proxy-safe) ────────────────────────────────────────

    /**
     * All cache annotations live here — every method is called through
     * the Spring proxy from OtpCacheServiceImpl, so AOP always fires.
     */
    @Component
    static class OtpStore {

        // signup otp
        @CachePut(value = "otp:signup", key = "#email")
        public String putSignupOtp(String email, String otp) { return otp; }

        @Cacheable(value = "otp:signup", key = "#email")
        public String fetchSignupOtp(String email) { return null; }

        @CacheEvict(value = "otp:signup", key = "#email")
        public void evictSignupOtp(String email) {}

        // signup verified
        @CachePut(value = "otp:verified", key = "#email")
        public String putSignupVerified(String email) { return "true"; }

        @Cacheable(value = "otp:verified", key = "#email")
        public String fetchSignupVerified(String email) { return null; }

        @CacheEvict(value = "otp:verified", key = "#email")
        public void evictSignupVerified(String email) {}

        // forgot password otp
        @CachePut(value = "otp:forgot", key = "#email")
        public String putForgotOtp(String email, String otp) { return otp; }

        @Cacheable(value = "otp:forgot", key = "#email")
        public String fetchForgotOtp(String email) { return null; }

        @CacheEvict(value = "otp:forgot", key = "#email")
        public void evictForgotOtp(String email) {}
    }
}
