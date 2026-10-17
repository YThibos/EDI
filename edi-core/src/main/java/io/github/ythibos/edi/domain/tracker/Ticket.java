package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

import io.github.ythibos.edi.domain.project.WorkKey;

public record Ticket(WorkKey key, String summary, String status) {

    public Ticket {
        Objects.requireNonNull(key, "A ticket has its key.");
        summary = Objects.requireNonNullElse(summary, "").strip();
        status = Objects.requireNonNullElse(status, "").strip();
    }

    public static Ticket keyedOnly(WorkKey key) {
        return new Ticket(key, "", "");
    }

    public boolean isDescribed() {
        return !summary.isEmpty();
    }
}
