package com.shrirang.distributed_promptforge.account_service.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String name,
        String role
) {
}
