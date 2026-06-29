package com.shrirang.distributed_promptforge.account_service.dto.auth;

public record AuthResponse(
        String token,
        UserProfileResponse user
) {

}
