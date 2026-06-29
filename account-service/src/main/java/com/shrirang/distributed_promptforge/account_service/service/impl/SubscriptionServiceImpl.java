package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.client.IntelligenceClient;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PlanLimitsResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PublicPlanResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.SubscriptionResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.UsageTodayResponse;
import com.shrirang.distributed_promptforge.account_service.entity.Plan;
import com.shrirang.distributed_promptforge.account_service.entity.Subscription;
import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.mapper.SubscriptionMapper;
import com.shrirang.distributed_promptforge.account_service.repository.PlanRepository;
import com.shrirang.distributed_promptforge.account_service.repository.SubscriptionRepository;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionCacheService;
import com.shrirang.distributed_promptforge.account_service.service.SubscriptionService;
import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.dto.UsageSnapshotDto;
import com.mayur.distributed_promptforge.common_lib.enums.SubscriptionStatus;
import com.mayur.distributed_promptforge.common_lib.error.ResourceNotFoundException;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final AuthUtil authUtil;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final IntelligenceClient intelligenceClient;
    private final SubscriptionCacheService subscriptionCacheService;

    private static final int FREE_TIER_TOKENS_PER_DAY = 100;
    private static final int FREE_TIER_PROJECTS_ALLOWED = 5;

    @Override
    public List<SubscriptionResponse> getCurrentSubscriptions() {
        Long userId = authUtil.getCurrentUserId();

        List<Subscription> activeSubscriptions = subscriptionRepository.findAllByUserIdAndStatusIn(userId, Set.of(
                SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.TRIALING
        ));

        List<Subscription> validSubscriptions = new java.util.ArrayList<>();
        boolean evicted = false;

        for (Subscription sub : activeSubscriptions) {
            if (sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isBefore(Instant.now())) {
                sub.setStatus(SubscriptionStatus.CANCELED);
                subscriptionRepository.save(sub);
                evicted = true;
            } else {
                validSubscriptions.add(sub);
            }
        }

        if (evicted) {
            subscriptionCacheService.evictPlan(userId);
        }

        return validSubscriptions.stream()
                .map(subscriptionMapper::toSubscriptionResponseSafe)
                .toList();
    }

    @Override
    public UsageTodayResponse getUsageToday() {
        Long userId = authUtil.getCurrentUserId();
        PlanDto plan = getCurrentSubscribedPlanByUser();
        int tokensLimit = (plan != null && plan.maxTokensPerDay() != null)
                ? plan.maxTokensPerDay() : FREE_TIER_TOKENS_PER_DAY;
        boolean unlimited = plan != null && Boolean.TRUE.equals(plan.unlimitedAi());

        int tokensUsed = 0;
        try {
            UsageSnapshotDto usageSnapshot = intelligenceClient.getTodayUsageByUserId(userId);
            tokensUsed = usageSnapshot != null && usageSnapshot.tokensUsed() != null ? usageSnapshot.tokensUsed() : 0;
        } catch (Exception exception) {
            log.warn("Failed to fetch usage snapshot from intelligence-service for userId={}. Falling back to 0.", userId, exception);
        }

        int effectiveLimit = unlimited ? Integer.MAX_VALUE : tokensLimit;
        int tokensRemaining = unlimited ? Integer.MAX_VALUE : Math.max(effectiveLimit - tokensUsed, 0);

        return new UsageTodayResponse(
                tokensUsed,
                effectiveLimit,
                tokensRemaining,
                effectiveLimit,
                0,
                10
        );
    }

    @Override
    public PlanLimitsResponse getPlanLimits() {
        PlanDto plan = getCurrentSubscribedPlanByUser();
        String planName = (plan != null && plan.name() != null) ? plan.name() : "FREE";
        int maxTokens = (plan != null && plan.maxTokensPerDay() != null) ? plan.maxTokensPerDay() : FREE_TIER_TOKENS_PER_DAY;
        int maxProjects = (plan != null && plan.maxProjects() != null) ? plan.maxProjects() : FREE_TIER_PROJECTS_ALLOWED;
        boolean unlimitedAi = plan != null && Boolean.TRUE.equals(plan.unlimitedAi());

        return new PlanLimitsResponse(planName, maxTokens, maxProjects, unlimitedAi);
    }

    @Override
    public void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId) {
        boolean exists = subscriptionRepository.existsByStripeSubscriptionId(subscriptionId);
        if (exists) return;

        User user = getUser(userId);
        Plan plan = getPlan(planId);

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .stripeSubscriptionId(subscriptionId)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(calculatePeriodEnd(plan, Instant.now()))
                .build();

        subscriptionRepository.save(subscription);
        subscriptionCacheService.evictPlan(userId);
    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscriptionRepository.save(subscription);
        subscriptionCacheService.evictPlan(subscription.getUser().getId());
    }

    @Override
    @Transactional
    public void updateSubscription(String gatewaySubscriptionId, SubscriptionStatus status,
                                   Instant periodStart, Instant periodEnd,
                                   Boolean cancelAtPeriodEnd, Long planId) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        boolean changed = false;

        if (status != null && status != subscription.getStatus()) {
            subscription.setStatus(status);
            changed = true;
        }
        if (periodStart != null && !periodStart.equals(subscription.getCurrentPeriodStart())) {
            subscription.setCurrentPeriodStart(periodStart);
            changed = true;
        }
        if (periodEnd != null && !periodEnd.equals(subscription.getCurrentPeriodEnd())) {
            subscription.setCurrentPeriodEnd(periodEnd);
            changed = true;
        }
        if (cancelAtPeriodEnd != null && cancelAtPeriodEnd != subscription.getCancelAtPeriodEnd()) {
            subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            changed = true;
        }
        if (planId != null && !planId.equals(subscription.getPlan().getId())) {
            subscription.setPlan(getPlan(planId));
            changed = true;
        }

        if (changed) {
            log.debug("Subscription updated: {}", gatewaySubscriptionId);
            subscriptionRepository.save(subscription);
            subscriptionCacheService.evictPlan(subscription.getUser().getId());
        }
    }

    @Override
    public void renewSubscriptionPeriod(String gatewaySubscriptionId, Instant periodStart, Instant periodEnd) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        Instant newStart = periodStart != null ? periodStart : subscription.getCurrentPeriodEnd();
        subscription.setCurrentPeriodStart(newStart);
        subscription.setCurrentPeriodEnd(periodEnd);

        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE
                || subscription.getStatus() == SubscriptionStatus.INCOMPLETE) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }

        subscriptionRepository.save(subscription);
        subscriptionCacheService.evictPlan(subscription.getUser().getId());
    }

    @Override
    public void markSubscriptionPastDue(String gatewaySubscriptionId) {
        Subscription subscription = getSubscription(gatewaySubscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            log.debug("Subscription already past due: {}", gatewaySubscriptionId);
            return;
        }
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(subscription);
        subscriptionCacheService.evictPlan(subscription.getUser().getId());
    }

    @Override
    public PlanDto getCurrentSubscribedPlanByUser() {
        Long userId = authUtil.getCurrentUserId();
        return getCurrentSubscribedPlanByUserId(userId);
    }

    @Override
    public PlanDto getCurrentSubscribedPlanByUserId(Long userId) {
        return subscriptionCacheService.getPlan(userId, () -> {
            // Cache miss — load from DB
            List<Subscription> activeSubscriptions = subscriptionRepository.findAllByUserIdAndStatusIn(userId, Set.of(
                    SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
                    SubscriptionStatus.TRIALING
            ));

            List<Subscription> validSubscriptions = new java.util.ArrayList<>();
            boolean evicted = false;

            for (Subscription sub : activeSubscriptions) {
                if (sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isBefore(Instant.now())) {
                    sub.setStatus(SubscriptionStatus.CANCELED);
                    subscriptionRepository.save(sub);
                    evicted = true;
                } else {
                    validSubscriptions.add(sub);
                }
            }

            if (evicted) {
                subscriptionCacheService.evictPlan(userId);
            }

            if (validSubscriptions.isEmpty()) {
                return null;
            }

            // Build a combined plan
            String combinedName = "";
            int totalMaxProjects = 0;
            int totalMaxTokensPerDay = 0;
            boolean combinedUnlimitedAi = false;

            for (Subscription sub : validSubscriptions) {
                Plan p = sub.getPlan();
                if (p != null) {
                    if (!combinedName.isEmpty()) {
                        combinedName += " + ";
                    }
                    combinedName += toDisplayName(p.getName());

                    totalMaxProjects += (p.getMaxProjects() != null) ? p.getMaxProjects() : 0;
                    totalMaxTokensPerDay += (p.getMaxTokensPerDay() != null) ? p.getMaxTokensPerDay() : 0;
                    if (Boolean.TRUE.equals(p.getUnlimitedAi())) {
                        combinedUnlimitedAi = true;
                    }
                }
            }

            return new PlanDto(
                    null,
                    combinedName,
                    totalMaxProjects,
                    totalMaxTokensPerDay,
                    combinedUnlimitedAi,
                    "0",
                    combinedName
            );
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void cancelSubscriptionById(Long id) {
        Long userId = authUtil.getCurrentUserId();
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id.toString()));
        if (!subscription.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this subscription");
        }
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscriptionRepository.save(subscription);
        subscriptionCacheService.evictPlan(userId);
    }

    @Override
    public List<PublicPlanResponse> getAvailablePlans() {
        return planRepository.findAll().stream()
                .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
                .sorted((a, b) -> {
                    long pa = a.getPriceInPaise() != null ? a.getPriceInPaise() : 0L;
                    long pb = b.getPriceInPaise() != null ? b.getPriceInPaise() : 0L;
                    return Long.compare(pa, pb);
                })
                .map(plan -> new PublicPlanResponse(
                        plan.getId(),
                        plan.getName(),
                        toDisplayName(plan.getName()),
                        plan.getPriceInPaise(),
                        plan.getMaxProjects(),
                        plan.getMaxTokensPerDay(),
                        plan.getUnlimitedAi(),
                        plan.getValidityDays(),
                        plan.getActive()
                ))
                .toList();
    }

    private Instant calculatePeriodEnd(Plan plan, Instant from) {
        int validityDays = (plan.getValidityDays() != null && plan.getValidityDays() > 0) ? plan.getValidityDays() : 30;
        return from.plusSeconds(validityDays * 24L * 60L * 60L);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private Plan getPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId.toString()));
    }

    private Subscription getSubscription(String gatewaySubscriptionId) {
        return subscriptionRepository.findByStripeSubscriptionId(gatewaySubscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", gatewaySubscriptionId));
    }

    private String toDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "Plan";
        }
        String lower = name.toLowerCase();
        return switch (lower) {
            case "free" -> "Free";
            case "pro" -> "Pro";
            case "team" -> "Team";
            case "pro_starter" -> "Pro Starter";
            case "pro_growth" -> "Pro Growth";
            case "pro_unlimited_sprint" -> "Pro Unlimited Sprint";
            default -> Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
        };
    }
}
