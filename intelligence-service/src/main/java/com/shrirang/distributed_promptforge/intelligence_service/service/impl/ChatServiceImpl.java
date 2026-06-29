package com.shrirang.distributed_promptforge.intelligence_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatResponse;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatMessage;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSession;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSessionId;
import com.shrirang.distributed_promptforge.intelligence_service.mapper.ChatMapper;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatMessageRepository;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatSessionRepository;
import com.shrirang.distributed_promptforge.intelligence_service.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AuthUtil authUtil;
    private final ChatMapper chatMapper;

    @Override
    public List<ChatResponse> getProjectChatHistory(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        ChatSessionId sessionId = new ChatSessionId(projectId, userId);

        ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder().id(sessionId).build()));

        List<ChatMessage> chatMessageList = chatMessageRepository.findByChatSession(chatSession);

        return chatMapper.fromListOfChatMessage(chatMessageList);
    }
}
