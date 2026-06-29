package com.shrirang.distributed_promptforge.account_service.dto.admin;

import com.shrirang.distributed_promptforge.account_service.entity.UserRole;

public record AdminUserResponse(
        Long id,
        String username,
        String name,
        UserRole role,
        Boolean blocked
) {
}
