package io.github.ythibos.edi.domain.authority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.TaskId;

class CaptainsAuthorityTest {

    private static final Instant OPENED = Instant.parse("2026-10-17T09:00:00Z");
    private static final UUID SESSION = UUID.randomUUID();
    private static final Exchange EXCHANGE = new Exchange(SESSION, OPENED);
    private static final TaskId LAMP = new TaskId("fit-lamp");

    @Test
    void takesTheLatestWordOnTheTaskFromThisSession() {
        List<Authorisation> words = List.of(
                new Authorisation(SESSION, LAMP, "Drop the lamp.", OPENED.plusSeconds(60)),
                new Authorisation(SESSION, LAMP, "Discard its work too.", OPENED.plusSeconds(120)),
                new Authorisation(UUID.randomUUID(), LAMP, "Discard everything.", OPENED.plusSeconds(180)),
                new Authorisation(SESSION, new TaskId("fit-door"), "Drop the door.", OPENED.plusSeconds(240)));

        assertThat(CaptainsAuthority.onTask(LAMP, EXCHANGE, words, List.of())).contains(new CaptainsWord("Discard its work too."));
        assertThat(CaptainsAuthority.onTask(new TaskId("fit-hinge"), EXCHANGE, words, List.of())).isEmpty();
    }

    @Test
    void countsADecisionAnsweredSinceTheSessionOpened() {
        CaptainAnswer before = answeredAt(OPENED.minusSeconds(1));
        CaptainAnswer during = answeredAt(OPENED.plusSeconds(1));

        assertThat(CaptainsAuthority.onTask(LAMP, EXCHANGE, List.of(), List.of(before))).isEmpty();
        assertThat(CaptainsAuthority.onTask(LAMP, EXCHANGE, List.of(), List.of(during))).contains(new CaptainsWord("D58 A"));
    }

    @Test
    void keepsAWordOnTheHomeApartFromAWordOnATask() {
        List<Authorisation> words = List.of(new Authorisation(SESSION, LAMP, "Read the token for the lamp.", OPENED.plusSeconds(60)));

        assertThat(CaptainsAuthority.onTheHome(EXCHANGE, words)).isEmpty();
        assertThat(CaptainsAuthority.onTheHome(EXCHANGE, List.of(new Authorisation(SESSION, null, "Read the token.", OPENED.plusSeconds(60)))))
                .contains(new CaptainsWord("Read the token."));
    }

    private static CaptainAnswer answeredAt(Instant at) {
        return new CaptainAnswer("D58 A", new OptionKey('A'), "", at, AnswerChannel.CONSOLE);
    }
}
