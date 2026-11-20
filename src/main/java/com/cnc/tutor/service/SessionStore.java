package com.cnc.tutor.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cnc.tutor.dto.ChatMessage;

/**
 * Historique en mémoire par session : on garde les N derniers messages.
 * Les sessions inactives depuis {@code chat.session-ttl} sont supprimées, et le
 * nombre total de sessions est plafonné (site public : la mémoire reste bornée).
 */
@Service
public class SessionStore {

    private static final class Session {
        final Deque<ChatMessage> messages = new ArrayDeque<>();
        volatile long lastAccess = System.currentTimeMillis();
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final long ttlMillis;
    private final int maxSessions;

    public SessionStore(int maxMessages) {
        this(maxMessages, Duration.ofHours(2), 2000);
    }

    @Autowired
    public SessionStore(@Value("${chat.history-size:10}") int maxMessages,
                        @Value("${chat.session-ttl:2h}") Duration ttl,
                        @Value("${chat.max-sessions:2000}") int maxSessions) {
        this.maxMessages = maxMessages;
        this.ttlMillis = ttl.toMillis();
        this.maxSessions = maxSessions;
    }

    public List<ChatMessage> history(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            return List.of();
        }
        s.lastAccess = System.currentTimeMillis();
        synchronized (s.messages) {
            return List.copyOf(s.messages);
        }
    }

    /** Ajoute un échange question/réponse et tronque aux N derniers messages. */
    public void append(String sessionId, ChatMessage... messages) {
        if (!sessions.containsKey(sessionId) && sessions.size() >= maxSessions) {
            evict();
        }
        Session s = sessions.computeIfAbsent(sessionId, k -> new Session());
        s.lastAccess = System.currentTimeMillis();
        synchronized (s.messages) {
            for (ChatMessage m : messages) {
                s.messages.addLast(m);
            }
            while (s.messages.size() > maxMessages) {
                s.messages.removeFirst();
            }
        }
    }

    public void reset(String sessionId) {
        sessions.remove(sessionId);
    }

    int size() {
        return sessions.size();
    }

    /** Supprime les sessions expirées ; si c'est insuffisant, les plus anciennes. */
    private synchronized void evict() {
        long limit = System.currentTimeMillis() - ttlMillis;
        sessions.values().removeIf(s -> s.lastAccess < limit);
        int excess = sessions.size() - maxSessions + 1;
        if (excess > 0) {
            sessions.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().lastAccess))
                    .limit(excess)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(sessions::remove);
        }
    }
}
