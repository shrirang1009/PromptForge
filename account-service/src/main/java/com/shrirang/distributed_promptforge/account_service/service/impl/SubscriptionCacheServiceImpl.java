package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.service.SubscriptionCacheService;
import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Redis-backed cache for the active subscription plan of a user using Spring Cache annotations.
 *
 * Cache name: plan:user
 * Key: plan:user:{userId}
 */
@Service
@Slf4j
public class SubscriptionCacheServiceImpl implements SubscriptionCacheService {

    @Override
    @Cacheable(value = "plan:user", key = "#userId")
    public PlanDto getPlan(Long userId, Supplier<PlanDto> dbLoader) {
        log.debug("Plan cache miss: loading from DB for userId={}", userId);
        return dbLoader.get();
    }

    @Override
    @CacheEvict(value = "plan:user", key = "#userId")
    public void evictPlan(Long userId) {
        log.debug("Plan cache evicted for userId={}", userId);
    }
}
