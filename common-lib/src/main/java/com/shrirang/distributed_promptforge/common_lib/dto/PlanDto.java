package com.shrirang.distributed_promptforge.common_lib.dto;

public record PlanDto(
        Long id,
        String name,
        Integer maxProjects,
        Integer maxTokensPerDay,
        Boolean unlimitedAi,
        String price,
        String displayName
) {
}