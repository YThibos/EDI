package io.github.ythibos.edi.domain.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ConsoleAccessRulesTest {

    private static final Instant ISSUED = Instant.parse("2026-10-17T09:00:00Z");

    @Test
    void aSignInLinkSignsInOnceAndOnlyWithinTwoMinutes() {
        SignInLink link = SignInLink.issued("digest", ISSUED);

        assertThat(link.signsInAt(ISSUED.plus(Duration.ofSeconds(119)))).isTrue();
        assertThat(link.signsInAt(ISSUED.plus(Duration.ofMinutes(2)))).isFalse();
        assertThat(link.used(ISSUED.plusSeconds(5)).signsInAt(ISSUED.plusSeconds(6))).isFalse();
    }

    @Test
    void aConsoleSessionStaysOpenForTwelveHours() {
        ConsoleSession session = ConsoleSession.opened("digest", ISSUED);

        assertThat(session.isOpenAt(ISSUED.plus(Duration.ofHours(12)).minusSeconds(1))).isTrue();
        assertThat(session.isOpenAt(ISSUED.plus(Duration.ofHours(12)))).isFalse();
    }
}
