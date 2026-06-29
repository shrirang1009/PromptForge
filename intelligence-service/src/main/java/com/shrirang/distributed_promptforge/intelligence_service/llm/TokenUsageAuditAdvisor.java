package com.shrirang.distributed_promptforge.intelligence_service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenUsageAuditAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(TokenUsageAuditAdvisor.class);
    public static final String USAGE_TRACKING_KEY = "usageTrackingKey";
    private static final Map<String, TokenUsageSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        ChatResponse chatResponse = chatClientResponse.chatResponse();

        if (chatResponse != null && chatResponse.getMetadata() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                int totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;

                if (totalTokens <= 0) {
                    totalTokens = promptTokens + completionTokens;
                }

                logger.info("Token usage for this call - Prompt Tokens: {}, Completion Tokens: {}, Total Tokens: {}",
                        promptTokens, completionTokens, totalTokens);
            } else {
                logger.warn("No token usage information available in the response metadata.");
            }
        }

        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain streamAdvisorChain) {
        String trackingKey = request.context() != null
                ? String.valueOf(request.context().getOrDefault(USAGE_TRACKING_KEY, ""))
                : "";

        final TokenUsageSnapshot[] latest = new TokenUsageSnapshot[]{null};

        return streamAdvisorChain.nextStream(request)
                .doOnNext(response -> {
                    if (response == null || response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
                        return;
                    }
                    Usage usage = response.chatResponse().getMetadata().getUsage();
                    if (usage == null) {
                        return;
                    }
                    int prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    int completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                    int total = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
                    if (total <= 0) {
                        total = prompt + completion;
                    }
                    latest[0] = new TokenUsageSnapshot(prompt, completion, total);
                })
                .doFinally(signalType -> {
                    if (!trackingKey.isBlank() && latest[0] != null) {
                        SNAPSHOTS.put(trackingKey, latest[0]);
                    }
                });
    }

    public static TokenUsageSnapshot consume(String trackingKey) {
        if (trackingKey == null || trackingKey.isBlank()) {
            return null;
        }
        return SNAPSHOTS.remove(trackingKey);
    }

    @Override
    public String getName() {
        return "TokenUsageAuditAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    public record TokenUsageSnapshot(int promptTokens, int completionTokens, int totalTokens) {
    }
}
