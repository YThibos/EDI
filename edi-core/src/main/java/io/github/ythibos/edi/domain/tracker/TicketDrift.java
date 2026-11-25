package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

import io.github.ythibos.edi.domain.project.WorkKey;

public record TicketDrift(WorkKey key, String status, DriftKind kind) {

    public TicketDrift {
        Objects.requireNonNull(key, "A drifting ticket has its key.");
        Objects.requireNonNull(kind, "A drift is of one kind.");
        status = Objects.requireNonNullElse(status, "").strip();
    }
}
