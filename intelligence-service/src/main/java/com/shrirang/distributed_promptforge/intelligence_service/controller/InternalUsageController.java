package com.shrirang.distributed_promptforge.intelligence_service.controller;

import com.mayur.distributed_promptforge.common_lib.dto.UsageSnapshotDto;
import com.shrirang.distributed_promptforge.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/usage")
@RequiredArgsConstructor
public class InternalUsageController {

    private final UsageService usageService;

    @GetMapping("/users/{userId}/today")
    public UsageSnapshotDto getTodayUsageByUserId(@PathVariable Long userId) {
        return new UsageSnapshotDto(usageService.getTodayTokenUsage(userId));
    }
}
