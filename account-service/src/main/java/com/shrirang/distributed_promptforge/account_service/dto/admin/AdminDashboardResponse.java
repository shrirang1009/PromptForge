package com.shrirang.distributed_promptforge.account_service.dto.admin;

public record AdminDashboardResponse(
        long totalUsers,
        long blockedUsers,
        long activeSubscriptions
) {
}
