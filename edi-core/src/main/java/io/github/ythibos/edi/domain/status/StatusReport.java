package io.github.ythibos.edi.domain.status;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;

public record StatusReport(StatusState state, StatusKey key, Instant until, String text) {

    public StatusReport {
        Objects.requireNonNull(state, "A status names its state.");
        text = Objects.requireNonNullElse(text, "").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A status says in one line what happened.");
        }
        if (state == StatusState.RESOLVED && key == null) {
            throw new IllegalArgumentException("A resolved status carries the key of what it resolves.");
        }
    }

    public static StatusReport fromWorker(StatusState state, StatusKey key, Instant until, String text, CaptainAddress address) {
        StatusReport report = new StatusReport(state, key, until, text);
        if (address.opens(report.text())) {
            throw new Refusal("A worker reports to EDI and never addresses the captain, so a status never opens with \""
                    + address.vocative() + "\" or \"Captain,\"; say what happened instead.");
        }
        return report;
    }

    public static StatusReport resolvedBy(StatusKey key, int message) {
        return new StatusReport(StatusState.RESOLVED, key, null, "resolved by message " + message);
    }

    public boolean isForThePrimary() {
        return state == StatusState.NEEDS_DECISION || state == StatusState.BLOCKED;
    }

    public boolean asks(StatusKey question) {
        return isForThePrimary() && question.equals(key);
    }

    public boolean isDone() {
        return state == StatusState.DONE;
    }
}
