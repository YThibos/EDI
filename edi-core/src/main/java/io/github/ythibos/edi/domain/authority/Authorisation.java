package io.github.ythibos.edi.domain.authority;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.task.TaskId;

public record Authorisation(UUID session, TaskId task, String verbatim, Instant saidAt) {

    public Authorisation {
        Objects.requireNonNull(session, "The captain's word is heard in a primary session.");
        Objects.requireNonNull(saidAt, "The captain's word records when it was given.");
        if (verbatim.isBlank()) {
            throw new IllegalArgumentException("The captain's word is the captain's own words.");
        }
    }

    public boolean concernsTheHome() {
        return task == null;
    }

    public boolean concerns(TaskId other) {
        return other.equals(task);
    }
}
