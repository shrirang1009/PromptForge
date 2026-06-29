package com.shrirang.distributed_promptforge.intelligence_service.service.impl;

import com.mayur.distributed_promptforge.common_lib.enums.ChatEventStatus;
import com.mayur.distributed_promptforge.common_lib.enums.ChatEventType;
import com.mayur.distributed_promptforge.common_lib.enums.MessageRole;
import com.mayur.distributed_promptforge.common_lib.event.FileStoreRequestEvent;
import com.mayur.distributed_promptforge.common_lib.security.AuthUtil;
import com.mayur.distributed_promptforge.common_lib.dto.FileNode;
import com.shrirang.distributed_promptforge.intelligence_service.client.WorkspaceClient;
import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.StreamResponse;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatEvent;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatMessage;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSession;
import com.shrirang.distributed_promptforge.intelligence_service.entity.ChatSessionId;
import com.shrirang.distributed_promptforge.intelligence_service.llm.CodeGenerationTools;
import com.shrirang.distributed_promptforge.intelligence_service.llm.LlmResponseParser;
import com.shrirang.distributed_promptforge.intelligence_service.llm.PromptUtils;
import com.shrirang.distributed_promptforge.intelligence_service.llm.TokenUsageAuditAdvisor;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatEventRepository;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatMessageRepository;
import com.shrirang.distributed_promptforge.intelligence_service.repository.ChatSessionRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.shrirang.distributed_promptforge.intelligence_service.service.AiGenerationService;
import com.shrirang.distributed_promptforge.intelligence_service.service.RateLimitService;
import com.shrirang.distributed_promptforge.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationServiceImpl implements AiGenerationService {
    private static final long RATE_LIMIT_COOLDOWN_MS = 180000L;
    private static final int MAX_FILE_TREE_ITEMS = 500;
    private static final int MAX_HISTORY_MESSAGES = 20; // last 10 turns (user+assistant)

    private final ChatClient chatClient;
    private final AuthUtil authUtil;
    private final RateLimitService rateLimitService;
    private final ChatSessionRepository chatSessionRepository;
    private final LlmResponseParser llmResponseParser;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatEventRepository chatEventRepository;
    private final UsageService usageService;
    private final WorkspaceClient workspaceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;


    @Override
    @PreAuthorize("@security.canEditProject(#p1)")
    public Flux<StreamResponse> streamResponse(String userMessage, Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        usageService.ensureWithinPlanBeforeCall(userId, userMessage);

        if (rateLimitService.isGlobalCooldownActive()) {
            String msg = "AI provider is rate-limited. Please try again shortly.";
            log.warn("AI request blocked by global cooldown: projectId={}, userId={}", projectId, userId);
            return Flux.just(new StreamResponse(msg));
        }

        if (rateLimitService.isUserInCooldown(userId)) {
            String msg = "Rate limit active. Please try again shortly.";
            log.warn("AI request blocked by user cooldown: projectId={}, userId={}", projectId, userId);
            return Flux.just(new StreamResponse(msg));
        }

        if (rateLimitService.isTooFrequent(userId)) {
            String msg = "Please wait a moment before sending another message.";
            log.warn("AI request blocked by min-gap guard: projectId={}, userId={}", projectId, userId);
            return Flux.just(new StreamResponse(msg));
        }
        rateLimitService.markLastRequest(userId);

        log.info("AI stream start: projectId={}, userId={}, messageLength={}",
                projectId, userId, userMessage != null ? userMessage.length() : 0);
        ChatSession chatSession = createChatSessionIfNotExists(projectId, userId);

        StringBuilder fullResponseBuffer = new StringBuilder();
        CodeGenerationTools codeGenerationTools = new CodeGenerationTools(projectId, workspaceClient);
        String systemPrompt = buildSystemPromptWithFileTree(projectId);
        List<Message> chatHistory = buildChatHistory(chatSession);

        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(0L);
        AtomicReference<Integer> chunkCount = new AtomicReference<>(0);
        AtomicBoolean finalized = new AtomicBoolean(false);
        String usageTrackingKey = UUID.randomUUID().toString();

        return chatClient.prompt()
                .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
                .system(systemPrompt)
                .messages(chatHistory)
                .user(userMessage)
                .tools(codeGenerationTools)
                .advisors(spec -> spec.param(TokenUsageAuditAdvisor.USAGE_TRACKING_KEY, usageTrackingKey))
                .stream()
                .chatResponse()
                .retryWhen(
                        Retry.backoff(2, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(8))
                                .filter(this::isRetryableTransientError)
                                .doBeforeRetry(signal -> log.warn(
                                        "AI upstream transient failure. Retrying attempt={} projectId={} userId={}",
                                        signal.totalRetries() + 1, projectId, userId
                                ))
                )
                .doOnNext(response -> {
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        String content = sanitizeAiText(response.getResult().getOutput().getText());
                        if (content.isEmpty()) {
                            return;
                        }
                        chunkCount.set(chunkCount.get() + 1);

                        if(endTime.get() == 0) { // first non-empty chunk received
                            endTime.set(System.currentTimeMillis());
                            long firstTokenLatencyMs = endTime.get() - startTime.get();
                            log.info("AI first chunk received: projectId={}, userId={}, latencyMs={}",
                                    projectId, userId, firstTokenLatencyMs);
                        }
                        fullResponseBuffer.append(content);
                    }

                })
                .doOnError(error -> log.error("Error during streaming: projectId={}, userId={}", projectId, userId, error))
                .doFinally(signalType -> {
                    if (!finalized.compareAndSet(false, true)) {
                        return;
                    }

                    Schedulers.boundedElastic().schedule(() -> {
                        long duration = endTime.get() == 0L
                                ? (System.currentTimeMillis() - startTime.get()) / 1000
                                : (endTime.get() - startTime.get()) / 1000;

                        if (chunkCount.get() == 0) {
                            log.warn("AI stream finished with zero chunks: projectId={}, userId={}, signal={}",
                                    projectId, userId, signalType);
                        } else {
                            log.info("AI stream finished: projectId={}, userId={}, signal={}, chunks={}, responseChars={}",
                                    projectId, userId, signalType, chunkCount.get(), fullResponseBuffer.length());
                        }

                        TokenUsageAuditAdvisor.TokenUsageSnapshot usageSnapshot = TokenUsageAuditAdvisor.consume(usageTrackingKey);
                        finalizeChats(userMessage, chatSession, fullResponseBuffer.toString(), duration, usageSnapshot, userId);
                    });
                })
                .map(response -> {
                    if (response.getResults() != null && !response.getResults().isEmpty()) {
                        String text = sanitizeAiText(response.getResult().getOutput().getText());
                        return new StreamResponse(text);
                    }
                    return new StreamResponse("");
                })
                .onErrorResume(error -> {
                    if (error instanceof ResponseStatusException responseStatusException) {
                        String reason = responseStatusException.getReason() != null
                                ? responseStatusException.getReason()
                                : "Request blocked by usage policy.";
                        return Flux.just(new StreamResponse(reason));
                    }
                    if (isRetryableRateLimit(error)) {
                        long cooldownMs = resolveCooldownMs(error);
                        rateLimitService.setUserCooldown(userId, cooldownMs);
                        rateLimitService.setGlobalCooldown(cooldownMs);
                        String msg = "AI service is temporarily rate-limited. Please retry in a few seconds.";
                        log.warn("AI stream fallback due to rate limit: projectId={}, userId={}, cooldownMs={}",
                                projectId, userId, cooldownMs);
                        return Flux.just(new StreamResponse(msg));
                    }
                    String msg = "AI response failed. Please try again.";
                    log.error("AI stream fallback due to error: projectId={}, userId={}", projectId, userId, error);
                    return Flux.just(new StreamResponse(msg));
                });
    }

    private List<Message> buildChatHistory(ChatSession chatSession) {
        List<ChatMessage> saved = chatMessageRepository.findByChatSession(chatSession);

        // Last N messages lo, pure history nahi (context window + cost ke liye)
        int fromIndex = Math.max(0, saved.size() - MAX_HISTORY_MESSAGES);
        List<ChatMessage> recent = saved.subList(fromIndex, saved.size());

        List<Message> history = new ArrayList<>();
        for (ChatMessage msg : recent) {
            if (msg.getRole() == MessageRole.USER) {
                history.add(new UserMessage(msg.getContent() != null ? msg.getContent() : ""));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                history.add(new AssistantMessage(msg.getContent() != null ? msg.getContent() : ""));
            }
        }

        log.debug("Chat history loaded: sessionProjectId={}, totalSaved={}, sentToAi={}",
                chatSession.getId().getProjectId(), saved.size(), history.size());
        return history;
    }

    private String buildSystemPromptWithFileTree(Long projectId) {
        try {
            List<FileNode> fileTree = workspaceClient.getFileTree(projectId).files();
            String summarized = fileTree.stream()
                    .limit(MAX_FILE_TREE_ITEMS)
                    .map(FileNode::path)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(empty project)");
            return "\n\n---- FILE_TREE ----\n" + summarized;
        } catch (Exception ex) {
            log.warn("Could not load FILE_TREE for projectId={}. Continuing without advisor context.", projectId, ex);
            return "\n\n---- FILE_TREE ----\n(unavailable)";
        }
    }

    private boolean isRetryableRateLimit(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webEx) {
            return webEx.getStatusCode().value() == 429;
        }
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof WebClientResponseException webEx && webEx.getStatusCode().value() == 429) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isRetryableTransientError(Throwable throwable) {
        if (isRetryableRateLimit(throwable)) {
            return false;
        }

        if (throwable instanceof WebClientResponseException webEx) {
            int code = webEx.getStatusCode().value();
            return code >= 500 && code < 600;
        }

        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof WebClientResponseException webEx) {
                int code = webEx.getStatusCode().value();
                return code >= 500 && code < 600;
            }
            cause = cause.getCause();
        }
        return true;
    }

    private long resolveCooldownMs(Throwable throwable) {
        long fallback = RATE_LIMIT_COOLDOWN_MS;
        WebClientResponseException rateLimitEx = findRateLimitException(throwable);
        if (rateLimitEx == null) {
            return fallback;
        }

        String retryAfter = rateLimitEx.getHeaders().getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return fallback;
        }

        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds <= 0) return fallback;
            // Keep cooldown sane (max 10 minutes)
            return Math.min(seconds * 1000L, 10 * 60 * 1000L);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private WebClientResponseException findRateLimitException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webEx && webEx.getStatusCode().value() == 429) {
            return webEx;
        }
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof WebClientResponseException webEx && webEx.getStatusCode().value() == 429) {
                return webEx;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String sanitizeAiText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("\u0000", "").replaceFirst("(?i)^(?:null)+", "");
        return cleaned.trim().equalsIgnoreCase("null") ? "" : cleaned;
    }

    private void finalizeChats(String userMessage, ChatSession chatSession, String fullText, Long duration,
                               TokenUsageAuditAdvisor.TokenUsageSnapshot usageSnapshot, Long userId) {
        Long projectId = chatSession.getId().getProjectId();

        int promptTokens = usageSnapshot != null ? usageSnapshot.promptTokens() : 0;
        int completionTokens = usageSnapshot != null ? usageSnapshot.completionTokens() : 0;
        int totalTokens = usageSnapshot != null ? usageSnapshot.totalTokens() : 0;

        boolean providerUsageReliable = totalTokens > 0 || promptTokens > 0 || completionTokens > 0;
        if (!providerUsageReliable) {
            log.warn("AI usage metadata missing/zero from advisor: projectId={}, userId={}", projectId, userId);
        } else {
            // Some providers return total=0 while component fields are populated.
            if (totalTokens <= 0) {
                totalTokens = promptTokens + completionTokens;
            }
            log.info("AI usage: projectId={}, userId={}, promptTokens={}, completionTokens={}, totalTokens={}",
                    projectId, userId, promptTokens, completionTokens, totalTokens);
        }

        final List<ChatEvent> chatEventList = new java.util.ArrayList<>();
        final int finalTotalTokens = totalTokens;

        transactionTemplate.executeWithoutResult(status -> {
            if (finalTotalTokens > 0) {
                usageService.recordTokenUsage(chatSession.getId().getUserId(), finalTotalTokens);
            }

            // Save the User message
            chatMessageRepository.save(
                    ChatMessage.builder()
                            .chatSession(chatSession)
                            .role(MessageRole.USER)
                            .content(userMessage)
                            .tokensUsed(promptTokens)
                            .build()
            );

            ChatMessage assistantChatMessage = ChatMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .content(fullText != null ? fullText : "")
                    .chatSession(chatSession)
                    .tokensUsed(completionTokens)
                    .build();

            ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantChatMessage);

            List<ChatEvent> parsedEvents = llmResponseParser.parseChatEvents(fullText, savedAssistantMessage);
            parsedEvents.addFirst(ChatEvent.builder()
                            .type(ChatEventType.THOUGHT)
                            .status(ChatEventStatus.CONFIRMED)
                            .chatMessage(savedAssistantMessage)
                            .content("Thought for "+duration+"s")
                            .sequenceOrder(0)
                    .build());

            // Initialize sagaIds for file edits inside the transaction so they are saved to DB
            parsedEvents.stream()
                    .filter(e -> e.getType() == ChatEventType.FILE_EDIT)
                    .forEach(e -> {
                        String sagaId = UUID.randomUUID().toString();
                        e.setSagaId(sagaId);
                    });

            chatEventRepository.saveAll(parsedEvents);
            chatEventList.addAll(parsedEvents);
        });

        // After transaction commits successfully, publish Kafka events
        chatEventList.stream()
                .filter(e -> e.getType() == ChatEventType.FILE_EDIT)
                .forEach(e -> {
                    FileStoreRequestEvent fileStoreRequestEvent = new FileStoreRequestEvent(
                            projectId,
                            e.getSagaId(),
                            e.getFilePath(),
                            e.getContent(),
                            userId
                    );
                    log.info("Storage request event sent: {}", e.getFilePath());
                    kafkaTemplate.send("file-storage-request-event", "project-"+projectId, fileStoreRequestEvent);
                });
    }

    private ChatSession createChatSessionIfNotExists(Long projectId, Long userId) {
        ChatSessionId chatSessionId = new ChatSessionId(projectId, userId);
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);

        if(chatSession == null) {
            chatSession = ChatSession.builder()
                    .id(chatSessionId)
                    .build();

            chatSession = chatSessionRepository.save(chatSession);
        }
        return chatSession;
    }
}
