package io.github.ythibos.edi.domain.wake;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

public record Wake(WakeKind kind, TaskId task, WorkerId worker, String detail, Instant moment) {

    public Wake {
        Objects.requireNonNull(kind, "A wake names what needs the primary.");
        Objects.requireNonNull(task, () -> "A " + kind.word() + " wake names its task.");
        detail = Objects.requireNonNullElse(detail, "").strip();
    }
}
