package com.shrirang.distributed_promptforge.intelligence_service.repository;

import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSession;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, ChatSessionId> {
}
