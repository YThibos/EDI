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
        report.requireAddressedToEdi(address);
        report.requireTheKeyOfAQuestion();
        report.requireTheEndOfAPause();
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

    private void requireAddressedToEdi(CaptainAddress address) {
        if (address.opens(text)) {
            throw new Refusal("A worker reports to EDI and never addresses the captain, so a status never opens with \""
                    + address.vocative() + "\" or \"Captain,\"; say what happened instead.");
        }
    }

    private void requireTheKeyOfAQuestion() {
        if (isForThePrimary() && key == null) {
            throw new Refusal("A " + state.word() + " status names what it waits on with --key, so the message that answers it can settle it.");
        }
    }

    private void requireTheEndOfAPause() {
        if (isAPause() && until == null) {
            throw new Refusal("A paused status says when the pause ends with --until, such as --until 2026-10-16T14:30:00Z, "
                    + "so a pause that overruns is noticed.");
        }
    }

    private boolean isAPause() {
        return state == StatusState.PAUSED;
    }
}
