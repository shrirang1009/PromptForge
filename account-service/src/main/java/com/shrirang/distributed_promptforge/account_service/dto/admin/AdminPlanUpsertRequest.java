package com.shrirang.distributed_promptforge.account_service.dto.admin;

public record AdminPlanUpsertRequest(
        String name,
        Long priceInPaise,
        Integer maxProjects,
        Integer maxTokensPerDay,
        Boolean unlimitedAi,
        Integer validityDays,
        Boolean active
) {
}
