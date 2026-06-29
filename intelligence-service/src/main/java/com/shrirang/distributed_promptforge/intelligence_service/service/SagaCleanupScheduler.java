package com.shrirang.distributed_promptforge.intelligence_service.service;

import com.mayur.distributed_promptforge.common_lib.enums.ChatEventStatus;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatEvent;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SagaCleanupScheduler {

    private final ChatEventRepository chatEventRepository;

    @Scheduled(fixedDelay = 60000) // Runs every minute
    @Transactional
    public void cleanupStaleSagas() {
        log.debug("Running stale saga cleanup check...");
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<ChatEvent> staleEvents = chatEventRepository.findAllByStatusAndChatMessageCreatedAtBefore(
                ChatEventStatus.PENDING,
                threshold
        );

        if (!staleEvents.isEmpty()) {
            log.warn("Found {} stale pending saga events. Marking them as FAILED due to timeout.", staleEvents.size());
            for (ChatEvent event : staleEvents) {
                event.setStatus(ChatEventStatus.FAILED);
                log.info("Saga {} marked as FAILED due to timeout (stale pending).", event.getSagaId());
            }
            chatEventRepository.saveAll(staleEvents);
        }
    }
}
