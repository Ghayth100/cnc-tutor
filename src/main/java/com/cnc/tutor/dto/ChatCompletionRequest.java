package com.cnc.tutor.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Corps de la requête POST /chat/completions (format OpenAI-compatible). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens,
        boolean stream,
        @JsonProperty("reasoning_effort") String reasoningEffort) {
}
