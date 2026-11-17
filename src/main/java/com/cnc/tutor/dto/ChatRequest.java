package com.cnc.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "sessionId est obligatoire") @Size(max = 100) String sessionId,
        @NotBlank(message = "Le message est vide") @Size(max = 4000, message = "Message trop long (4000 caractères max)") String message) {
}
