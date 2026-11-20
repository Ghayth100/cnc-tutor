package com.cnc.tutor.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cnc.tutor.exception.TooManyQuestionsException;

/**
 * Limite le nombre de questions par visiteur (adresse IP) et par minute,
 * pour qu'un seul visiteur ne puisse pas épuiser le quota de la clé API.
 */
@Service
public class ChatRateLimiter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;

    public ChatRateLimiter(@Value("${chat.rate-limit-per-minute:3}") int perMinute) {
        this.perMinute = perMinute;
    }

    /** Lève {@link TooManyQuestionsException} si le visiteur a dépassé sa limite. */
    public void check(String clientId) {
        if (perMinute <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MS);
        }
        Window w = windows.compute(clientId, (k, old) ->
                old == null || now - old.start() >= WINDOW_MS ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));
        if (w.count() > perMinute) {
            long wait = Math.max(1, (WINDOW_MS - (now - w.start())) / 1000);
            throw new TooManyQuestionsException(perMinute, wait);
        }
    }
}
