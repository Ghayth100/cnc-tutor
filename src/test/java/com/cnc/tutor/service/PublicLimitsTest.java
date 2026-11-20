package com.cnc.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.cnc.tutor.dto.ChatMessage;
import com.cnc.tutor.exception.TooManyQuestionsException;

/** Garde-fous pour un hébergement public : limite par visiteur et mémoire bornée. */
class PublicLimitsTest {

    @Test
    void laLimiteParVisiteurBloqueAuDelaDuQuota() {
        ChatRateLimiter limiter = new ChatRateLimiter(3);
        for (int i = 0; i < 3; i++) {
            limiter.check("1.2.3.4");
        }
        assertThatThrownBy(() -> limiter.check("1.2.3.4"))
                .isInstanceOf(TooManyQuestionsException.class)
                .hasMessageContaining("3 questions par minute");
        // un autre visiteur n'est pas affecté
        assertThatCode(() -> limiter.check("5.6.7.8")).doesNotThrowAnyException();
    }

    @Test
    void zeroDesactiveLaLimite() {
        ChatRateLimiter limiter = new ChatRateLimiter(0);
        for (int i = 0; i < 100; i++) {
            limiter.check("x");
        }
    }

    @Test
    void leNombreDeSessionsEstPlafonne() {
        SessionStore store = new SessionStore(10, Duration.ofHours(2), 3);
        for (int i = 0; i < 10; i++) {
            store.append("s" + i, ChatMessage.user("q"));
        }
        assertThat(store.size()).isLessThanOrEqualTo(3);
        assertThat(store.history("s9")).hasSize(1); // la plus récente est conservée
        assertThat(store.history("s0")).isEmpty();  // la plus ancienne a été supprimée
    }
}
