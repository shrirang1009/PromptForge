package com.shrirang.distributed_promptforge.intelligence_service.service;


import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatResponse;

import java.util.List;

public interface ChatService {

    List<ChatResponse> getProjectChatHistory(Long projectId);
}
