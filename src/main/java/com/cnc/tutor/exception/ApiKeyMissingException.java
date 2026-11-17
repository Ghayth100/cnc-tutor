package com.cnc.tutor.exception;

public class ApiKeyMissingException extends RuntimeException {

    public ApiKeyMissingException() {
        super("Clé API absente : définissez la variable d'environnement LLM_API_KEY puis redémarrez l'application.");
    }
}
