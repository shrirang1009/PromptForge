package com.shrirang.distributed_promptforge.account_service.client.fallback;

import com.shrirang.distributed_promptforge.account_service.client.IntelligenceClient;
import com.mayur.distributed_promptforge.common_lib.dto.UsageSnapshotDto;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IntelligenceClientFallback implements IntelligenceClient {

    @Override
    public UsageSnapshotDto getTodayUsageByUserId(Long userId) {
        log.warn("IntelligenceClientFallback: Intelligence Service is down. getTodayUsageByUserId failed for userId={}. Returning zero usage.", userId);
        return new UsageSnapshotDto(0);
    }
}
