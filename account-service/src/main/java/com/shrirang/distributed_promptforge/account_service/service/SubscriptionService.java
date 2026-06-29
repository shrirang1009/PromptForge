package com.shrirang.distributed_promptforge.account_service.service;

import com.shrirang.distributed_promptforge.account_service.dto.subscription.PlanLimitsResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.PublicPlanResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.SubscriptionResponse;
import com.shrirang.distributed_promptforge.account_service.dto.subscription.UsageTodayResponse;
import com.mayur.distributed_promptforge.common_lib.dto.PlanDto;
import com.mayur.distributed_promptforge.common_lib.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.List;

public interface SubscriptionService {

    List<SubscriptionResponse> getCurrentSubscriptions();

    void cancelSubscriptionById(Long id);

    UsageTodayResponse getUsageToday();

    PlanLimitsResponse getPlanLimits();

    void activateSubscription(Long userId, Long planId, String subscriptionId, String customerId);

    void updateSubscription(String gatewaySubscriptionId, SubscriptionStatus status, Instant periodStart,
                            Instant periodEnd, Boolean cancelAtPeriodEnd, Long planId);

    void cancelSubscription(String gatewaySubscriptionId);

    void renewSubscriptionPeriod(String subId, Instant periodStart, Instant periodEnd);

    void markSubscriptionPastDue(String subId);

    PlanDto getCurrentSubscribedPlanByUser();

    PlanDto getCurrentSubscribedPlanByUserId(Long userId);

    List<PublicPlanResponse> getAvailablePlans();
}
