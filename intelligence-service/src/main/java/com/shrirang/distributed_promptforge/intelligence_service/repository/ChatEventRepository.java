package com.shrirang.distributed_promptforge.intelligence_service.repository;

import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mayur.distributed_promptforge.common_lib.enums.ChatEventStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
    Optional<ChatEvent> findBySagaId(String s);

    @Query("SELECT e FROM ChatEvent e JOIN e.chatMessage m WHERE e.status = :status AND m.createdAt < :threshold")
    List<ChatEvent> findAllByStatusAndChatMessageCreatedAtBefore(
            @Param("status") ChatEventStatus status,
            @Param("threshold") java.time.Instant threshold
    );
}
