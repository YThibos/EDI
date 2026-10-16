package io.github.ythibos.edi.domain.task;

import java.time.Instant;

public record CaptainHold(HoldKind kind, String reason, HoldUntil until, Instant setAt) {

    public CaptainHold {
        reason = reason.strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("A hold says why the task waits for the captain.");
        }
    }

    public boolean awaitsADecision() {
        return kind == HoldKind.DECISION;
    }
}
