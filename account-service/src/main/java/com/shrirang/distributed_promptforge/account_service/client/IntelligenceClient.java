package com.shrirang.distributed_promptforge.account_service.client;

import com.mayur.distributed_promptforge.common_lib.dto.UsageSnapshotDto;
import com.shrirang.distributed_promptforge.account_service.client.fallback.IntelligenceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "intelligence-service", fallback = IntelligenceClientFallback.class)
public interface IntelligenceClient {

    @GetMapping("/internal/v1/usage/users/{userId}/today")
    UsageSnapshotDto getTodayUsageByUserId(@PathVariable("userId") Long userId);
}
