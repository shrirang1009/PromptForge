package com.shrirang.distributed_promptforge.intelligence_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.intelligence_service.client.AccountClient;
import com.shrirang.distributed_promptforge.intelligence_service.entity.UsageLog;
import com.shrirang.distributed_promptforge.intelligence_service.repository.UsageLogRepository;
import com.shrirang.distributed_promptforge.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;

/**
 * UsageService with Redis-backed atomic daily token counter.
 *
 * Redis key: usage:tokens:{userId}:{yyyy-MM-dd}  (String, incremented atomically)
 * TTL: 25 hours so it survives until well past midnight.
 *
 * The counter is the source-of-truth for rate checks.
 * DB is still written asynchronously for historical records and reporting.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class UsageServiceImpl implements UsageService {

    private static final String USAGE_KEY_PREFIX = "usage:tokens:";
    private static final Duration USAGE_TTL = Duration.ofHours(25);

    private final UsageLogRepository usageLogRepository;
    private final AuthUtil authUtil;
    private final AccountClient accountClient;
    private final RedisTemplate<String, String> redisTemplate;

    // ── helpers ────────────────────────────────────────────────────────────────

    private String redisKey(Long userId) {
        return USAGE_KEY_PREFIX + userId + ":" + LocalDate.now();
    }

    // ── public API ─────────────────────────────────────────────────────────────

    @Override
    public void recordTokenUsage(Long userId, int actualTokens) {
        if (actualTokens <= 0) return;

        // 1. Atomic Redis increment (main counter)
        String key = redisKey(userId);
        Long newTotal = redisTemplate.opsForValue().increment(key, actualTokens);
        // Set TTL only on first increment (key just created).
        // BUG FIX: Long == Long is object reference comparison in Java — always use .longValue()
        // to compare primitive values, otherwise two different Long objects with the same
        // numeric value will NOT be equal via ==, causing TTL to never be set.
        if (newTotal != null && newTotal.longValue() == (long) actualTokens) {
            redisTemplate.expire(key, USAGE_TTL);
        }
        log.debug("Token usage recorded in Redis: userId={}, tokens={}, total={}", userId, actualTokens, newTotal);

        // 2. Persist to DB asynchronously for history / reporting (upsert to avoid race condition)
        LocalDate today = LocalDate.now();
        try {
            usageLogRepository.incrementTokens(userId, today, actualTokens);
        } catch (Exception e) {
            // Fallback: find-or-create + save
            log.warn("Upsert failed for userId={}, falling back to find-or-create: {}", userId, e.getMessage());
            UsageLog todayLog = usageLogRepository.findByUserIdAndDate(userId, today)
                    .orElseGet(() -> createNewDailyLog(userId, today));
            todayLog.setTokensUsed(todayLog.getTokensUsed() + actualTokens);
            usageLogRepository.save(todayLog);
        }
    }

    @Override
    public void checkDailyTokensUsage() {
        Long userId = authUtil.getCurrentUserId();
        PlanDto plan = accountClient.getCurrentSubscribedPlanByUserId(userId);

        if (plan == null || Boolean.TRUE.equals(plan.unlimitedAi())) {
            return;
        }

        int limit = plan.maxTokensPerDay() != null ? plan.maxTokensPerDay() : 100;
        int used  = getTodayTokenUsage(userId);

        if (used >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily limit reached, Upgrade now");
        }
    }

    @Override
    public int getTodayTokenUsage(Long userId) {
        String raw = redisTemplate.opsForValue().get(redisKey(userId));
        if (raw != null) {
            try { return Integer.parseInt(raw); } catch (NumberFormatException ignored) {}
        }
        // Fallback to DB if Redis key not yet populated.
        // BUG FIX: After loading from DB, re-seed the Redis counter so subsequent calls
        // within the same day do not keep hitting the DB unnecessarily. This also ensures
        // that if Redis was restarted and lost all keys, the counter is restored from DB
        // rather than starting from zero (which would allow users to exceed their daily limit).
        int dbTokens = usageLogRepository.findByUserIdAndDate(userId, LocalDate.now())
                .map(log -> log.getTokensUsed() != null ? log.getTokensUsed() : 0)
                .orElse(0);
        if (dbTokens > 0) {
            String key = redisKey(userId);
            try {
                redisTemplate.opsForValue().set(key, String.valueOf(dbTokens), USAGE_TTL);
                log.debug("Redis usage counter re-seeded from DB: userId={}, tokens={}", userId, dbTokens);
            } catch (Exception e) {
                log.warn("Failed to re-seed Redis usage counter for userId={}: {}", userId, e.getMessage());
            }
        }
        return dbTokens;
    }

    @Override
    public void ensureWithinPlanBeforeCall(Long userId, String promptText) {
        PlanDto plan = accountClient.getCurrentSubscribedPlanByUserId(userId);
        if (plan == null || Boolean.TRUE.equals(plan.unlimitedAi())) {
            return;
        }

        int limit = plan.maxTokensPerDay() != null ? plan.maxTokensPerDay() : 100;
        int used  = getTodayTokenUsage(userId);
        int projectedPromptTokens = estimateTokenCount(promptText);

        if (used >= limit || (used + projectedPromptTokens) > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily token limit reached for your plan. Please upgrade to continue.");
        }
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private UsageLog createNewDailyLog(Long userId, LocalDate date) {
        UsageLog newLog = UsageLog.builder()
                .userId(userId)
                .date(date)
                .tokensUsed(0)
                .build();
        return usageLogRepository.save(newLog);
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 1;
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
