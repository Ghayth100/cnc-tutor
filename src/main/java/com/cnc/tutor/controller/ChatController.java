package com.cnc.tutor.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cnc.tutor.dto.ChatRequest;
import com.cnc.tutor.dto.ResetRequest;
import com.cnc.tutor.exception.ApiKeyMissingException;
import com.cnc.tutor.exception.GlobalExceptionHandler;
import com.cnc.tutor.service.LlmClient;
import com.cnc.tutor.service.TutorService;

import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;

/**
 * POST /api/chat  → flux SSE : évènements « token » {"t": "..."}, puis « done » ou « error ».
 * POST /api/reset → vide l'historique de la session.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final TutorService tutorService;
    private final LlmClient llmClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(TutorService tutorService, LlmClient llmClient) {
        this.tutorService = tutorService;
        this.llmClient = llmClient;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        // Vérification synchrone : renvoie une erreur JSON claire via @ControllerAdvice
        if (!llmClient.isConfigured()) {
            throw new ApiKeyMissingException();
        }
        SseEmitter emitter = new SseEmitter(0L); // pas de timeout côté serveur (géré par LlmClient)
        executor.execute(() -> {
            try {
                tutorService.ask(request.sessionId(), request.message(),
                        delta -> send(emitter, "token", Map.of("t", delta)));
                send(emitter, "done", Map.of());
                emitter.complete();
            } catch (ClientGoneException e) {
                log.debug("Client déconnecté pendant le streaming");
            } catch (Exception e) {
                log.warn("Échec de la réponse : {}", e.toString());
                try {
                    send(emitter, "error", GlobalExceptionHandler.toError(e));
                    emitter.complete();
                } catch (ClientGoneException ignored) {
                    // rien à faire
                }
            }
        });
        return emitter;
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetRequest request) {
        tutorService.reset(request.sessionId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException(e);
        }
    }

    /** Le navigateur a fermé la connexion : on interrompt la génération. */
    private static final class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
