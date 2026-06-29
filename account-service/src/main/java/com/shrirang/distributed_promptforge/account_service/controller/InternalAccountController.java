package com.shrirang.distributed_promptforge.account_service.controller;

import com.shrirang.distributed_promptforge.account_service.mapper.UserMapper;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionService;
import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.dto.UserDto;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalAccountController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SubscriptionService subscriptionService;

    @GetMapping("/users/{id}")
    public UserDto getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(userMapper::toUserDto)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
    }

    @GetMapping("/users/by-email")
    public Optional<UserDto> getUserByEmail(@RequestParam String email) {
        return userRepository.findByUsernameIgnoreCase(email)
                .map(userMapper::toUserDto);
    }

    @GetMapping("/billing/current-plan")
    public PlanDto getCurrentSubscribedPlan(@RequestParam Long userId) {
        return subscriptionService.getCurrentSubscribedPlanByUserId(userId);
    }
}
