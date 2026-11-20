package com.cnc.tutor.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import com.cnc.tutor.dto.ErrorResponse;

/**
 * Transforme les erreurs en messages clairs affichés dans le chat.
 * {@link #toError(Throwable)} est aussi utilisé pour les erreurs survenant
 * pendant le streaming SSE (envoyées comme évènement « error »).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiKeyMissingException.class)
    public ResponseEntity<ErrorResponse> apiKeyMissing(ApiKeyMissingException e) {
        return json(HttpStatus.SERVICE_UNAVAILABLE, toError(e));
    }

    @ExceptionHandler(TooManyQuestionsException.class)
    public ResponseEntity<ErrorResponse> tooMany(TooManyQuestionsException e) {
        return json(HttpStatus.TOO_MANY_REQUESTS, new ErrorResponse("TOO_MANY_QUESTIONS", e.getMessage()));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> llm(LlmException e) {
        HttpStatus status = switch (e.getKind()) {
            case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return json(status, toError(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst().orElse("Requête invalide");
        return json(HttpStatus.BAD_REQUEST, new ErrorResponse("BAD_REQUEST", msg));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> status(ResponseStatusException e) {
        return json(HttpStatus.valueOf(e.getStatusCode().value()),
                new ErrorResponse("HTTP_" + e.getStatusCode().value(), e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> other(Exception e) {
        log.error("Erreur inattendue", e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, toError(e));
    }

    /** Message utilisateur pour n'importe quelle erreur (partagé avec le flux SSE). */
    public static ErrorResponse toError(Throwable e) {
        if (e instanceof ApiKeyMissingException) {
            return new ErrorResponse("API_KEY_MISSING", e.getMessage());
        }
        if (e instanceof LlmException le) {
            return switch (le.getKind()) {
                case RATE_LIMIT -> new ErrorResponse("RATE_LIMIT",
                        "Trop de requêtes vers l'API (erreur 429). Patientez une minute puis réessayez. " + le.getMessage());
                case TIMEOUT -> new ErrorResponse("TIMEOUT",
                        "L'API n'a pas répondu à temps (timeout). Réessayez dans un instant.");
                case UNAUTHORIZED -> new ErrorResponse("UNAUTHORIZED",
                        "Clé API refusée (401). Vérifiez la valeur de LLM_API_KEY.");
                case REQUEST_TOO_LARGE -> new ErrorResponse("REQUEST_TOO_LARGE",
                        "Requête trop volumineuse pour la limite de votre compte API (413). " + le.getMessage());
                case UPSTREAM -> new ErrorResponse("UPSTREAM", "Erreur de l'API LLM : " + le.getMessage());
            };
        }
        return new ErrorResponse("INTERNAL", "Erreur interne du serveur. Consultez les logs.");
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
