package com.shrirang.distributed_promptforge.intelligence_service.dto.chat;


import com.mayur.distributed_promptforge.common_lib.enums.MessageRole;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        Long id,
        MessageRole role,
        List<ChatEventResponse> events,
        String content,
        Integer tokensUsed,
        Instant createdAt

) {
}
