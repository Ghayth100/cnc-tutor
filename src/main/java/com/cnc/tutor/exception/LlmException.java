package com.cnc.tutor.exception;

/** Erreur lors de l'appel à l'API LLM, qualifiée par un {@link Kind}. */
public class LlmException extends RuntimeException {

    public enum Kind { RATE_LIMIT, TIMEOUT, UNAUTHORIZED, REQUEST_TOO_LARGE, UPSTREAM }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
