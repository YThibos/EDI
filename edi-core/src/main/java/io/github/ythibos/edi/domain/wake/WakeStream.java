package io.github.ythibos.edi.domain.wake;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.primary.SessionProcess;

public record WakeStream(UUID id, UUID session, SessionProcess process, boolean follows, Instant startedAt) {

    public WakeStream {
        Objects.requireNonNull(id, "A wake stream names itself.");
        Objects.requireNonNull(session, "A wake stream serves one Claude Code session.");
        Objects.requireNonNull(process, "A wake stream runs as a process.");
        Objects.requireNonNull(startedAt, "A wake stream records when it started.");
    }

    public boolean isTheSameAs(WakeStream other) {
        return id.equals(other.id);
    }

    public boolean serves(UUID otherSession) {
        return session.equals(otherSession);
    }

    public String command() {
        return follows ? "edi wait --follow" : "edi wait";
    }
}
