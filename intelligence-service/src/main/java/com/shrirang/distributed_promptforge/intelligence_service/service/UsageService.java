package com.shrirang.distributed_promptforge.intelligence_service.service;

public interface UsageService {
    void recordTokenUsage(Long userId, int actualTokens);
    void checkDailyTokensUsage();
    int getTodayTokenUsage(Long userId);
    void ensureWithinPlanBeforeCall(Long userId, String promptText);
}
