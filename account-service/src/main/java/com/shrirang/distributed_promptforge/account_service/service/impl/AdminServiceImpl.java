package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminDashboardResponse;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminPlanUpsertRequest;
import com.shrirang.distributed_promptforge.account_service.dto.admin.AdminUserResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PublicPlanResponse;
import com.shrirang.distributed_promptforge.account_service.entity.Plan;
import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.repository.PlanRepository;
import com.shrirang.distributed_promptforge.account_service.repository.SubscriptionRepository;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.shrirang.distributed_promptforge.account_service.service.AdminService;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionCacheService;
import com.mayur.distributed_promptforge.common_lib.enums.SubscriptionStatus;
import com.mayur.distributed_promptforge.common_lib.error.BadRequestException;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCacheService subscriptionCacheService;

    // BUG FIX 1: Admin deleteUser — Redis me user ka cached plan entry orphan reh jaati thi.
    // Ab deleteUser() se pehle subscriptionCacheService.evictPlan(userId) call karte hain.
    @Override
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", String.valueOf(userId));
        }
        // Evict cached plan before deleting user so stale data is not served
        subscriptionCacheService.evictPlan(userId);
        userRepository.deleteById(userId);
    }

    // BUG FIX 2: Admin updatePlan / deletePlan / activatePlan — Plan DB me change hoti thi
    // lekin sabhi users ka cached plan Redis me stale rehta tha.
    // Ab plan mutation ke baad un sabhi users ka cache evict karte hain jinki
    // active subscription us plan se linked hai.
    @Override
    public void activatePlan(Long planId, boolean active) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));
        plan.setActive(active);
        planRepository.save(plan);
        // Evict all users subscribed to this plan
        evictAllUsersForPlan(planId);
    }

    @Override
    public void updatePlan(Long planId, AdminPlanUpsertRequest request) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(planId)));
        validatePlanRequest(request, planId);
        applyPlan(plan, request);
        planRepository.save(plan);
        // Evict all users subscribed to this plan so they get fresh limits
        evictAllUsersForPlan(planId);
    }

    @Override
    public void deletePlan(Long planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Plan", String.valueOf(planId));
        }
        if (subscriptionRepository.existsByPlanId(planId)) {
            throw new BadRequestException("Plan is assigned to existing subscriptions and cannot be deleted");
        }
        planRepository.deleteById(planId);
        // No active subscribers at this point (checked above), but evict defensively
        evictAllUsersForPlan(planId);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Evict Redis plan cache for every user who has an active subscription on this plan.
     * Called whenever a plan is mutated or deleted so no user is served stale limits.
     */
    private void evictAllUsersForPlan(Long planId) {
        subscriptionRepository.findAllByPlanId(planId).forEach(subscription ->
                subscriptionCacheService.evictPlan(subscription.getUser().getId())
        );
    }

    // ── unchanged methods below ────────────────────────────────────────────────

    @Override
    public AdminDashboardResponse getDashboard() {
        long totalUsers = userRepository.count();
        long blockedUsers = userRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getBlocked())).count();
        long activeSubscriptions = subscriptionRepository.findAll().stream()
                .filter(s -> Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE).contains(s.getStatus()))
                .count();
        return new AdminDashboardResponse(totalUsers, blockedUsers, activeSubscriptions);
    }

    @Override
    public List<AdminUserResponse> getUsers(String query) {
        List<User> users = (query == null || query.isBlank()) ? userRepository.findAll() : userRepository.search(query);
        return users.stream()
                .map(u -> new AdminUserResponse(u.getId(), u.getUsername(), u.getName(), u.getRole(), u.getBlocked()))
                .toList();
    }

    @Override
    public void setUserBlocked(Long userId, boolean blocked) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(userId)));
        user.setBlocked(blocked);
        userRepository.save(user);
        // BUG FIX 5: When a user is blocked, their cached plan entry in Redis must be evicted.
        // Without this, a blocked user's plan cache remains valid until TTL expires (5 min),
        // allowing them to continue making AI calls that pass the plan-limit check.
        // Evicting here forces a fresh DB lookup on the next request, where the block can be enforced.
        if (blocked) {
            subscriptionCacheService.evictPlan(userId);
        }
    }

    @Override
    public List<PublicPlanResponse> getPlans() {
        return planRepository.findAll().stream()
                .map(this::toPublicPlanResponse)
                .toList();
    }

    @Override
    public void createPlan(AdminPlanUpsertRequest request) {
        validatePlanRequest(request, null);
        Plan plan = new Plan();
        applyPlan(plan, request);
        planRepository.save(plan);
    }

    private PublicPlanResponse toPublicPlanResponse(Plan plan) {
        return new PublicPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getName(),
                plan.getPriceInPaise(),
                plan.getMaxProjects(),
                plan.getMaxTokensPerDay(),
                plan.getUnlimitedAi(),
                plan.getValidityDays(),
                plan.getActive()
        );
    }

    private void applyPlan(Plan plan, AdminPlanUpsertRequest request) {
        plan.setName(request.name());
        plan.setPriceInPaise(request.priceInPaise());
        plan.setMaxProjects(request.maxProjects());
        plan.setMaxTokensPerDay(request.maxTokensPerDay());
        plan.setUnlimitedAi(request.unlimitedAi());
        plan.setValidityDays(request.validityDays());
        plan.setActive(request.active());
    }

    private void validatePlanRequest(AdminPlanUpsertRequest request, Long currentPlanId) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Plan name is required");
        }
        if (request.priceInPaise() == null || request.priceInPaise() < 0) {
            throw new BadRequestException("Plan price must be zero or greater");
        }
        if (request.validityDays() == null || request.validityDays() <= 0) {
            throw new BadRequestException("Plan validity days must be greater than zero");
        }
        if (request.maxProjects() != null && request.maxProjects() < 0) {
            throw new BadRequestException("Max projects cannot be negative");
        }
        if (request.maxTokensPerDay() != null && request.maxTokensPerDay() < 0) {
            throw new BadRequestException("Max AI tokens per day cannot be negative");
        }

        planRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> currentPlanId == null || !existing.getId().equals(currentPlanId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Plan already exists with name: " + request.name());
                });
    }
}
