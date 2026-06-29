package com.shrirang.distributed_promptforge.account_service.repository;

import com.shrirang.distributed_promptforge.account_service.entity.Subscription;
import com.mayur.distributed_promptforge.common_lib.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /*
    * Get the current active subscription
    * */
    Optional<Subscription> findByUserIdAndStatusIn(Long userId, Set<SubscriptionStatus> statusSet);

    List<Subscription> findAllByUserIdAndStatusIn(Long userId, Set<SubscriptionStatus> statusSet);

    boolean existsByStripeSubscriptionId(String subscriptionId);

    Optional<Subscription> findByStripeSubscriptionId(String gatewaySubscriptionId);

    boolean existsByPlanId(Long planId);

    // BUG FIX SUPPORT: Used by AdminServiceImpl to evict Redis cache for all users on a given plan
    // when the plan is updated, activated/deactivated, or deleted.
    List<Subscription> findAllByPlanId(Long planId);
}
