package com.shrirang.distributed_promptforge.account_service.dto.subscription;

public record PublicPlanResponse(
        Long id,
        String name,
        String displayName,
        Long priceInPaise,
        Integer maxProjects,
        Integer maxTokensPerDay,
        Boolean unlimitedAi,
        Integer validityDays,
        Boolean active
) {
}
