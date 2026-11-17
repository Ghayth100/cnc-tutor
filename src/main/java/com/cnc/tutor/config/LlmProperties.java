package com.cnc.tutor.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Paramètres de l'API LLM (préfixe {@code llm.*} dans application.properties).
 * La clé API provient exclusivement de la variable d'environnement LLM_API_KEY.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String model,
        String apiKey,
        @DefaultValue("0.3") double temperature,
        @DefaultValue("2048") int maxTokens,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("120s") Duration readTimeout,
        String reasoningEffort) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
