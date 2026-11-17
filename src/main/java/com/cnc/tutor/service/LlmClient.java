package com.cnc.tutor.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.cnc.tutor.config.LlmProperties;
import com.cnc.tutor.dto.ChatCompletionChunk;
import com.cnc.tutor.dto.ChatCompletionRequest;
import com.cnc.tutor.dto.ChatMessage;
import com.cnc.tutor.exception.ApiKeyMissingException;
import com.cnc.tutor.exception.LlmException;
import com.cnc.tutor.exception.LlmException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Appel HTTP à une API OpenAI-compatible (POST {base-url}/chat/completions, stream=true).
 * Les fragments SSE « data: {...} » sont décodés et transmis au fur et à mesure.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public LlmClient(LlmProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        // HTTP/1.1 : évite la montée h2c sur les serveurs locaux (Ollama, proxys…)
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.readTimeout());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .build();
    }

    public boolean isConfigured() {
        return props.hasApiKey();
    }

    /**
     * Envoie la conversation et diffuse la réponse token par token.
     *
     * @return la réponse complète
     */
    public String streamChat(List<ChatMessage> messages, Consumer<String> onDelta) {
        if (!props.hasApiKey()) {
            throw new ApiKeyMissingException();
        }
        ChatCompletionRequest body = new ChatCompletionRequest(
                props.model(), messages, props.temperature(), props.maxTokens(), true,
                props.reasoningEffort() == null || props.reasoningEffort().isBlank() ? null : props.reasoningEffort());
        try {
            // Corps sérialisé en byte[] → Content-Length explicite (pas de transfert « chunked »)
            byte[] json = mapper.writeValueAsBytes(body);
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .body(json)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (status.isError()) {
                            String err = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw toException(status.value(), err);
                        }
                        return readStream(resp.getBody(), onDelta);
                    }, true);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de la requête impossible", e);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new LlmException(Kind.TIMEOUT, "Délai dépassé", e);
            }
            throw new LlmException(Kind.UPSTREAM, "API injoignable (" + e.getMostSpecificCause().getMessage() + ")", e);
        }
    }

    private String readStream(java.io.InputStream in, Consumer<String> onDelta) throws IOException {
        StringBuilder full = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).strip();
                if (data.equals("[DONE]")) {
                    break;
                }
                if (data.isEmpty()) {
                    continue;
                }
                String text = mapper.readValue(data, ChatCompletionChunk.class).text();
                if (!text.isEmpty()) {
                    full.append(text);
                    onDelta.accept(text);
                }
            }
        } catch (SocketTimeoutException | HttpTimeoutException e) {
            throw new LlmException(Kind.TIMEOUT, "Délai dépassé pendant la réponse", e);
        }
        return full.toString();
    }

    private LlmException toException(int status, String body) {
        String detail = extractErrorMessage(body);
        log.warn("API LLM : HTTP {} - {}", status, detail);
        return switch (status) {
            case 429 -> new LlmException(Kind.RATE_LIMIT, detail);
            case 401, 403 -> new LlmException(Kind.UNAUTHORIZED, detail);
            case 413 -> new LlmException(Kind.REQUEST_TOO_LARGE, detail);
            case 408, 504 -> new LlmException(Kind.TIMEOUT, detail);
            default -> new LlmException(Kind.UPSTREAM, "HTTP " + status + " : " + detail);
        };
    }

    /** Extrait error.message d'une réponse d'erreur OpenAI-compatible. */
    private String extractErrorMessage(String body) {
        try {
            JsonNode msg = mapper.readTree(body).path("error").path("message");
            if (msg.isTextual()) {
                return msg.asText();
            }
        } catch (IOException ignored) {
            // corps non JSON
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
