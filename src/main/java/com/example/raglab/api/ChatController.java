package com.example.raglab.api;

import com.example.raglab.application.ChatRequest;
import com.example.raglab.application.ChatResponse;
import com.example.raglab.application.ChatService;
import com.example.raglab.application.AuthUser;
import com.example.raglab.application.ConversationHistoryService;
import com.example.raglab.application.ChatTraceService;
import com.example.raglab.application.PagedResult;
import com.example.raglab.infrastructure.auth.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    private final ConversationHistoryService history;
    private final ChatTraceService traces;
    public ChatController(ChatService service, ConversationHistoryService history, ChatTraceService traces) {
        this.service = service;
        this.history = history;
        this.traces = traces;
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationHistoryService.ConversationView createConversation(HttpServletRequest request) {
        return history.create(userId(request));
    }

    @GetMapping("/conversations")
    public List<ConversationHistoryService.ConversationView> listConversations(HttpServletRequest request) {
        return history.list(userId(request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ConversationHistoryService.Turn> messages(@PathVariable String conversationId,
            HttpServletRequest request) {
        return history.recent(userId(request), conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages/page")
    public PagedResult<ConversationHistoryService.Turn> messagePage(@PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return history.page(userId(request), conversationId, page, size);
    }

    @GetMapping("/conversations/{conversationId}/traces")
    public PagedResult<ChatTraceService.Trace> traces(@PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return traces.page(userId(request), conversationId, page, size);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String conversationId, HttpServletRequest request) {
        long userId = userId(request);
        history.delete(userId, conversationId);
        traces.delete(userId, conversationId);
    }

    @PostMapping("/ask")
    public ChatResponse ask(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        return service.ask(request, userId(httpRequest));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        long userId = userId(httpRequest);
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                service.stream(request, userId, sources -> send(emitter, "sources", sources),
                        token -> send(emitter, "token", Map.of("text", token)),
                        progress -> send(emitter, "status", progress));
                send(emitter, "done", Map.of());
                emitter.complete();
            } catch (RuntimeException exception) {
                try {
                    send(emitter, "error", Map.of("message", exception.getMessage() == null
                            ? "Chat request failed" : exception.getMessage()));
                    emitter.complete();
                } catch (RuntimeException disconnected) {
                    emitter.completeWithError(disconnected);
                }
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("Chat stream disconnected", exception);
        }
    }

    private long userId(HttpServletRequest request) {
        AuthUser user = (AuthUser) request.getAttribute(BearerAuthenticationFilter.USER_ATTRIBUTE);
        if (user == null) throw new IllegalStateException("Authenticated user is required");
        return user.userId();
    }
}
