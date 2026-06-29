package com.shrirang.distributed_promptforge.intelligence_service.mapper;

import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatEventResponse;
import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatResponse;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatEvent;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatMapper {

    public ChatResponse fromChatMessage(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return null;
        }

        return new ChatResponse(
                chatMessage.getId(),
                chatMessage.getRole(),
                fromListOfChatEvent(chatMessage.getEvents()),
                chatMessage.getContent(),
                chatMessage.getTokensUsed(),
                chatMessage.getCreatedAt()
        );
    }

    public List<ChatResponse> fromListOfChatMessage(List<ChatMessage> chatMessageList) {
        if (chatMessageList == null) {
            return null;
        }

        return chatMessageList.stream()
                .map(this::fromChatMessage)
                .toList();
    }

    public ChatEventResponse fromChatEvent(ChatEvent chatEvent) {
        if (chatEvent == null) {
            return null;
        }

        return new ChatEventResponse(
                chatEvent.getId(),
                chatEvent.getType(),
                chatEvent.getSequenceOrder(),
                chatEvent.getContent(),
                chatEvent.getFilePath(),
                chatEvent.getMetadata()
        );
    }

    public List<ChatEventResponse> fromListOfChatEvent(List<ChatEvent> chatEvents) {
        if (chatEvents == null) {
            return null;
        }

        return chatEvents.stream()
                .map(this::fromChatEvent)
                .toList();
    }
}
