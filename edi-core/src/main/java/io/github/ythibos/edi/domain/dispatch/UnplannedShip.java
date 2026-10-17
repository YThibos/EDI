package io.github.ythibos.edi.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.TaskId;

public record UnplannedShip(TaskId ship, CategoryId category, TaskId research, Instant dispatchedAt) {

    public UnplannedShip {
        Objects.requireNonNull(ship, "An unplanned ship is a task.");
        Objects.requireNonNull(category, () -> ship + " records the category it was dispatched in.");
        Objects.requireNonNull(dispatchedAt, () -> ship + " records when it was dispatched.");
        if (ship.equals(research)) {
            throw new IllegalArgumentException(ship + " cannot plan itself.");
        }
    }

    public boolean isPiloted() {
        return research != null;
    }
}
