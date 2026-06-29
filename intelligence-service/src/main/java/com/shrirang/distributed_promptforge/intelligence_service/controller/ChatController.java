package com.shrirang.distributed_promptforge.intelligence_service.controller;

import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatRequest;
import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.ChatResponse;
import com.shrirang.distributed_promptforge.intelligence_service.dto.chat.StreamResponse;
import com.shrirang.distributed_promptforge.intelligence_service.service.AiGenerationService;
import com.shrirang.distributed_promptforge.intelligence_service.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    private final AiGenerationService aiGenerationService;
    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody @Valid ChatRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 min timeout
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication != null ? String.valueOf(authentication.getPrincipal()) : "anonymous";
        log.info("Chat stream request received. projectId={}, messageLength={}, principal={}",
                request.projectId(), request.message() != null ? request.message().length() : 0, principal);

        try {
            Flux<StreamResponse> flux = aiGenerationService.streamResponse(request.message(), request.projectId());
            final Disposable[] subscriptionRef = new Disposable[1];
            emitter.onCompletion(() -> {
                streamClosed.set(true);
                if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                    subscriptionRef[0].dispose();
                }
                log.info("SSE emitter completed callback for project {}", request.projectId());
            });
            emitter.onTimeout(() -> {
                streamClosed.set(true);
                if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                    subscriptionRef[0].dispose();
                }
                log.warn("SSE emitter timed out for project {}", request.projectId());
            });
            emitter.onError(error -> {
                streamClosed.set(true);
                if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                    subscriptionRef[0].dispose();
                }
                if (isClientDisconnect(error)) {
                    log.info("SSE emitter closed by client disconnect for project {}", request.projectId());
                } else {
                    log.error("SSE emitter error callback for project {}", request.projectId(), error);
                }
            });

            subscriptionRef[0] = flux.subscribe(
                    chunk -> {
                        if (streamClosed.get()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event().data(chunk, MediaType.APPLICATION_JSON));
                        } catch (Exception e) {
                            streamClosed.set(true);
                            if (subscriptionRef[0] != null && !subscriptionRef[0].isDisposed()) {
                                subscriptionRef[0].dispose();
                            }
                            if (isClientDisconnect(e)) {
                                log.info("SSE send stopped after client disconnect for project {}", request.projectId());
                            } else {
                                log.warn("SSE send failed for project {}", request.projectId(), e);
                            }
                            emitter.complete();
                        }
                    },
                    error -> {
                        if (streamClosed.get()) {
                            return;
                        }
                        streamClosed.set(true);
                        if (isClientDisconnect(error)) {
                            log.info("Chat stream ended due to client disconnect for project {}", request.projectId());
                            emitter.complete();
                        } else {
                            log.error("Chat stream failed for project {}", request.projectId(), error);
                            emitter.completeWithError(error);
                        }
                    },
                    () -> {
                        if (streamClosed.get()) {
                            return;
                        }
                        streamClosed.set(true);
                        log.info("Chat stream completed for project {}", request.projectId());
                        emitter.complete();
                    }
            );
        } catch (Exception e) {
            if (e instanceof AuthorizationDeniedException) {
                log.warn("Chat stream denied: projectId={}, principal={}", request.projectId(), principal);
                try {
                    emitter.send(SseEmitter.event().data(
                            new StreamResponse("You don't have permission to edit this project."),
                            MediaType.APPLICATION_JSON
                    ));
                } catch (IOException ioException) {
                    log.warn("Unable to send permission denied message for project {}", request.projectId(), ioException);
                }
                emitter.complete();
            } else if (e instanceof IllegalStateException) {
                log.warn("Chat stream blocked due to transient permission check issue: projectId={}, principal={}, reason={}",
                        request.projectId(), principal, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            new StreamResponse("Permission service temporarily unavailable. Please retry."),
                            MediaType.APPLICATION_JSON
                    ));
                } catch (IOException ioException) {
                    log.warn("Unable to send transient permission failure message for project {}", request.projectId(), ioException);
                }
                emitter.complete();
            } else {
                log.error("Failed to initialize chat stream for project {}", request.projectId(), e);
                emitter.completeWithError(e);
            }
        }

        return emitter;
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName();
            if ("org.springframework.web.context.request.async.AsyncRequestNotUsableException".equals(className)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("disconnected client")
                        || lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("connection abort")
                        || lower.contains("forcibly closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    @GetMapping("/projects/{projectId}")
    @PreAuthorize("@security.canViewProject(#p0)")
    public ResponseEntity<List<ChatResponse>> getChatHistory(
            @PathVariable Long projectId) {

        return ResponseEntity.ok(chatService.getProjectChatHistory(projectId));
    }
}
