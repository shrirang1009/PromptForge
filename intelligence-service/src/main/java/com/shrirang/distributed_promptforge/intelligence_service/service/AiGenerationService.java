package com.shrirang.distributed_promptforge.intelligence_service.service;

import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.StreamResponse;
import reactor.core.publisher.Flux;

public interface AiGenerationService {
    Flux<StreamResponse> streamResponse(String message, Long projectId);
}
