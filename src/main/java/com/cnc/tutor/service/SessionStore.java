package com.cnc.tutor.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cnc.tutor.dto.ChatMessage;

/** Historique en mémoire par session : on garde les N derniers messages. */
@Service
public class SessionStore {

    private final ConcurrentHashMap<String, Deque<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final int maxMessages;

    public SessionStore(@Value("${chat.history-size:10}") int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public List<ChatMessage> history(String sessionId) {
        Deque<ChatMessage> d = sessions.get(sessionId);
        if (d == null) {
            return List.of();
        }
        synchronized (d) {
            return List.copyOf(d);
        }
    }

    /** Ajoute un échange question/réponse et tronque aux N derniers messages. */
    public void append(String sessionId, ChatMessage... messages) {
        Deque<ChatMessage> d = sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (d) {
            for (ChatMessage m : messages) {
                d.addLast(m);
            }
            while (d.size() > maxMessages) {
                d.removeFirst();
            }
        }
    }

    public void reset(String sessionId) {
        sessions.remove(sessionId);
    }
}
