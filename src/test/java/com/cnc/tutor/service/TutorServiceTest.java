package com.cnc.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cnc.tutor.dto.ChatMessage;
import com.cnc.tutor.exception.LlmException;

class TutorServiceTest {

    private static final String COURS = "[Page 1]\nIntroduction\n\n[Page 2]\nG83 X… Y… Z… R… Q… F…";

    private LlmClient llmClient;
    private SessionStore sessionStore;
    private TutorService service;

    @BeforeEach
    void setUp() {
        CourseLoader courseLoader = mock(CourseLoader.class);
        when(courseLoader.getFullText()).thenReturn(COURS);
        llmClient = mock(LlmClient.class);
        sessionStore = new SessionStore(10);
        // Cours court → envoyé en entier
        service = new TutorService(new CourseRetriever(courseLoader, 100_000), llmClient, sessionStore, 3000);
    }

    @SuppressWarnings("unchecked")
    private void llmRepond(String... fragments) {
        doAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(1);
            StringBuilder sb = new StringBuilder();
            for (String f : fragments) {
                onDelta.accept(f);
                sb.append(f);
            }
            return sb.toString();
        }).when(llmClient).streamChat(anyList(), any());
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> messagesEnvoyes() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, org.mockito.Mockito.atLeastOnce()).streamChat(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void envoieLePromptSystemeAvecLeCoursPuisLaQuestion() {
        llmRepond("Le cycle ", "G83 (page 2)");

        List<String> recus = new ArrayList<>();
        String reponse = service.ask("s1", "Explique G83", recus::add);

        assertThat(reponse).isEqualTo("Le cycle G83 (page 2)");
        assertThat(recus).containsExactly("Le cycle ", "G83 (page 2)");

        List<ChatMessage> msgs = messagesEnvoyes();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).role()).isEqualTo("system");
        assertThat(msgs.get(0).content())
                .contains("professeur de fabrication mécanique")
                .contains("(page X)")
                .contains(COURS)
                .doesNotContain("<<<TEXTE DU COURS");
        assertThat(msgs.get(1)).isEqualTo(ChatMessage.user("Explique G83"));
    }

    @Test
    void lHistoriqueEstEnvoyeAuTourSuivant() {
        llmRepond("Réponse 1");
        service.ask("s1", "Question 1", t -> { });
        llmRepond("Réponse 2");
        service.ask("s1", "Question 2", t -> { });

        List<ChatMessage> msgs = messagesEnvoyes();
        assertThat(msgs).extracting(ChatMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(msgs.get(1).content()).isEqualTo("Question 1");
        assertThat(msgs.get(2).content()).isEqualTo("Réponse 1");
    }

    @Test
    void lHistoriqueEstLimiteADixMessages() {
        llmRepond("ok");
        for (int i = 0; i < 8; i++) {
            service.ask("s1", "Q" + i, t -> { });
        }
        assertThat(sessionStore.history("s1")).hasSize(10);
        assertThat(sessionStore.history("s1").getFirst().content()).isEqualTo("Q3");
    }

    @Test
    void lesSessionsSontIsoleesEtResetVideLHistorique() {
        llmRepond("ok");
        service.ask("a", "Qa", t -> { });
        service.ask("b", "Qb", t -> { });
        assertThat(sessionStore.history("a")).hasSize(2);

        service.reset("a");
        assertThat(sessionStore.history("a")).isEmpty();
        assertThat(sessionStore.history("b")).hasSize(2);
    }

    @Test
    void uneErreurApiNeModifiePasLHistorique() {
        when(llmClient.streamChat(anyList(), any()))
                .thenThrow(new LlmException(LlmException.Kind.RATE_LIMIT, "quota"));

        assertThatThrownBy(() -> service.ask("s1", "Question", t -> { }))
                .isInstanceOf(LlmException.class);
        assertThat(sessionStore.history("s1")).isEmpty();
    }
}
