package com.shrirang.distributed_promptforge.workspace_service.client.fallback;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.dto.UserDto;
import com.shrirang.distributed_promptforge.workspace_service.client.AccountClient;
import org.springframework.stereotype.Component;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AccountClientFallback implements AccountClient {

    @Override
    public UserDto getUserById(Long id) {
        log.warn("AccountClientFallback: Account Service is down. getUserById failed for id={}. Returning default UserDto.", id);
        return new UserDto(id, "unknown_user", "Unknown User");
    }

    @Override
    public Optional<UserDto> getUserByEmail(String email) {
        log.warn("AccountClientFallback: Account Service is down. getUserByEmail failed for email={}", email);
        return Optional.empty();
    }

    @Override
    public PlanDto getCurrentSubscribedPlanByUserId(Long userId) {
        log.warn("AccountClientFallback: Account Service is down. getCurrentSubscribedPlanByUserId failed for userId={}. Returning default FREE plan.", userId);
        return new PlanDto(null, "FREE", 5, 100, false, "0.0", "Free Tier");
    }
}
