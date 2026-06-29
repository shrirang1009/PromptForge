package com.shrirang.distributed_promptforge.account_service.service;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import java.util.function.Supplier;

/**
 * Redis-backed cache for the active subscription plan of a user.
 * Supports caching plan:user key with a 5 minutes TTL.
 */
public interface SubscriptionCacheService {

    PlanDto getPlan(Long userId, Supplier<PlanDto> dbLoader);

    void evictPlan(Long userId);
}
