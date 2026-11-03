package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;

public record Exchange(UUID session, Instant openedAt) {

    public Exchange {
        Objects.requireNonNull(session, "An exchange with the captain is one primary session.");
        Objects.requireNonNull(openedAt, () -> "The exchange in session " + session + " records when it opened.");
    }

    public boolean holds(Authorisation word) {
        return word.session().equals(session);
    }

    public boolean holds(CaptainMessage message) {
        return message.session().equals(session);
    }

    public boolean holds(CaptainAnswer answer) {
        return !answer.answeredAt().isBefore(openedAt);
    }
}
