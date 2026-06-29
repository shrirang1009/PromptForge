package com.shrirang.distributed_promptforge.account_service.mapper;

import com.shrirang.distributed_promptforge.account_service.dto.subscription.SubscriptionResponse;
import com.shrirang.distributed_promptforge.account_service.entity.Plan;
import com.shrirang.distributed_promptforge.account_service.entity.Subscription;
import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public SubscriptionResponse toSubscriptionResponseSafe(Subscription subscription) {
        if (subscription == null) {
            return new SubscriptionResponse(null, null, "FREE", null, 0L);
        }
        return toSubscriptionResponse(subscription);
    }

    public SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        if (subscription == null) {
            return null;
        }

        String status = subscription.getStatus() != null ? subscription.getStatus().name() : "FREE";
        return new SubscriptionResponse(
                subscription.getId(),
                toPlanResponse(subscription.getPlan()),
                status,
                subscription.getCurrentPeriodEnd(),
                0L
        );
    }

    public PlanDto toPlanResponse(Plan plan) {
        if (plan == null) {
            return null;
        }
        return new PlanDto(
                plan.getId(),
                plan.getName(),
                plan.getMaxProjects(),
                plan.getMaxTokensPerDay(),
                plan.getUnlimitedAi(),
                formatPrice(plan.getPriceInPaise()),
                toDisplayName(plan.getName())
        );
    }

    private String formatPrice(Long priceInPaise) {
        if (priceInPaise == null) {
            return null;
        }
        if (priceInPaise == 0) {
            return "0";
        }
        return String.valueOf(priceInPaise / 100);
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
