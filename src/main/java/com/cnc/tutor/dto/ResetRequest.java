package com.cnc.tutor.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetRequest(@NotBlank(message = "sessionId est obligatoire") String sessionId) {
}
