package com.cnc.tutor.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Fragment de réponse en streaming : {"choices":[{"delta":{"content":"..."}}]}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionChunk(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Delta delta, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String content) {
    }

    /** Texte contenu dans ce fragment (chaîne vide si aucun). */
    public String text() {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        Delta d = choices.getFirst().delta();
        return d == null || d.content() == null ? "" : d.content();
    }
}
