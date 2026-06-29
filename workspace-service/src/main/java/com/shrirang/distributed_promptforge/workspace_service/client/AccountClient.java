package com.shrirang.distributed_promptforge.workspace_service.client;

import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.shrirang.distributed_promptforge.workspace_service.client.fallback.AccountClientFallback;
import java.util.Optional;

@FeignClient(name = "account-service", fallback = AccountClientFallback.class)
public interface AccountClient {

    @GetMapping("/internal/v1/users/{id}")
    UserDto getUserById(@PathVariable("id") Long id);

    @GetMapping("/internal/v1/users/by-email")
    Optional<UserDto> getUserByEmail(@RequestParam("email") String email);

    @GetMapping("/internal/v1/billing/current-plan")
    PlanDto getCurrentSubscribedPlanByUserId(@RequestParam("userId") Long userId);
}
