package com.shrirang.distributed_promptforge.account_service.service;

public interface OtpCacheService {

    // ── Signup OTP ───────────────────────────────────────────────────────────

    /** Store signup OTP for given email (TTL 15 min via cache config). */
    void saveSignupOtp(String email, String otp);

    /** Retrieve stored signup OTP; returns null if expired or not found. */
    String getSignupOtp(String email);

    /** Delete signup OTP after successful verification. */
    void deleteSignupOtp(String email);

    // ── Signup Verified flag ─────────────────────────────────────────────────

    /** Mark email as OTP-verified (TTL 10 min via cache config). */
    void saveSignupVerified(String email);

    /** Check if email is marked as verified; returns null if expired or not found. */
    String getSignupVerified(String email);

    /** Delete verified flag after account creation completes. */
    void deleteSignupVerified(String email);

    // ── Forgot Password OTP ──────────────────────────────────────────────────

    /** Store forgot-password OTP for given email (TTL 15 min via cache config). */
    void saveForgotOtp(String email, String otp);

    /** Retrieve stored forgot-password OTP; returns null if expired or not found. */
    String getForgotOtp(String email);

    /** Delete forgot-password OTP after password reset completes. */
    void deleteForgotOtp(String email);
}
