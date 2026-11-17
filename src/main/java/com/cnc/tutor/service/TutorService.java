package com.cnc.tutor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cnc.tutor.dto.ChatMessage;
import com.cnc.tutor.prompt.TutorPrompt;

/** Assemble prompt système + cours + historique + question, puis interroge le LLM. */
@Service
public class TutorService {

    private final CourseRetriever courseRetriever;
    private final LlmClient llmClient;
    private final SessionStore sessionStore;
    private final int historyMaxChars;

    public TutorService(CourseRetriever courseRetriever, LlmClient llmClient, SessionStore sessionStore,
                        @Value("${chat.history-max-chars:3000}") int historyMaxChars) {
        this.courseRetriever = courseRetriever;
        this.llmClient = llmClient;
        this.sessionStore = sessionStore;
        this.historyMaxChars = historyMaxChars;
    }

    /**
     * Pose une question au tuteur ; chaque fragment de réponse est transmis à {@code onDelta}.
     * L'historique n'est mis à jour qu'en cas de succès.
     */
    public String ask(String sessionId, String question, Consumer<String> onDelta) {
        List<ChatMessage> messages = buildMessages(sessionId, question);
        String answer = llmClient.streamChat(messages, onDelta);
        sessionStore.append(sessionId, ChatMessage.user(question), ChatMessage.assistant(answer));
        return answer;
    }

    List<ChatMessage> buildMessages(String sessionId, String question) {
        List<ChatMessage> history = recentHistory(sessionId);

        // La recherche de pages tient compte de la dernière question (questions de suivi : « et G03 ? »)
        String query = question;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role().equals("user")) {
                query = history.get(i).content() + " " + question;
                break;
            }
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(TutorPrompt.build(courseRetriever.contextFor(query))));
        messages.addAll(history);
        messages.add(ChatMessage.user(question));
        return messages;
    }

    /** Derniers messages (10 max dans le SessionStore) dans la limite de {@code historyMaxChars}. */
    private List<ChatMessage> recentHistory(String sessionId) {
        List<ChatMessage> all = sessionStore.history(sessionId);
        int total = 0;
        int start = all.size();
        while (start > 0) {
            int len = all.get(start - 1).content().length();
            if (total + len > historyMaxChars) {
                break;
            }
            total += len;
            start--;
        }
        // On commence toujours par une question de l'étudiant
        if (start < all.size() && all.get(start).role().equals("assistant")) {
            start++;
        }
        return all.subList(start, all.size());
    }

    public void reset(String sessionId) {
        sessionStore.reset(sessionId);
    }
}
