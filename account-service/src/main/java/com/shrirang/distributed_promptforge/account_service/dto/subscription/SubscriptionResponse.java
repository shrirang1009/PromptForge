package com.shrirang.distributed_promptforge.account_service.dto.subscription;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        PlanDto plan,
        String status,
        Instant currentPeriodEnd,
        Long tokensUsedThisCycle
) {
}
