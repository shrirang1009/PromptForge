package com.shrirang.distributed_promptforge.intelligence_service.config;

import com.shrirang.distributed_promptforge.intelligence_service.llm.TokenUsageAuditAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private final TokenUsageAuditAdvisor tokenUsageAuditAdvisor;

    public AiConfig(TokenUsageAuditAdvisor tokenUsageAuditAdvisor) {
        this.tokenUsageAuditAdvisor = tokenUsageAuditAdvisor;
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return  builder
                .defaultAdvisors(
                        tokenUsageAuditAdvisor
                )
                .build();
    }
}
