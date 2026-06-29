package com.shrirang.distributed_promptforge.intelligence_service.dto.chat;


import com.mayur.distributed_promptforge.common_lib.enums.ChatEventType;

public record ChatEventResponse(
        Long id,
        ChatEventType type,
        Integer sequenceOrder,
        String content,
        String filePath,
        String metadata
) {
}
