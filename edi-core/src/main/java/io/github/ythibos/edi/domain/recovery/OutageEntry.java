package io.github.ythibos.edi.domain.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.WorkerId;

public record OutageEntry(int number, TaskId task, WorkerId worker, DeathCause cause, Instant listedAt) {

    public OutageEntry {
        if (number < 1) {
            throw new IllegalArgumentException("The outage hold list is numbered from 1: " + number);
        }
        Objects.requireNonNull(task, "An outage entry holds a task.");
        Objects.requireNonNull(worker, () -> "The outage entry for " + task + " names its dead worker.");
        Objects.requireNonNull(cause, () -> "The outage entry for " + task + " says why its worker died.");
        Objects.requireNonNull(listedAt, () -> "The outage entry for " + task + " records when it was listed.");
    }

    public static int nextNumberAfter(List<OutageEntry> open) {
        return open.stream().mapToInt(OutageEntry::number).max().orElse(0) + 1;
    }

    public CaptainHold hold() {
        return new CaptainHold(HoldKind.APPROVAL, "lost when " + cause.sentence() + "; number " + number
                + " on the outage hold list waits for the captain to resume it or start it fresh", HoldUntil.captainsWord(), listedAt);
    }

    public boolean holds(CaptainHold taskHold) {
        return hold().equals(taskHold);
    }

    public Task withoutItsHold(Task task) {
        return task.isHeld() && holds(task.hold()) ? task.released() : task;
    }
}
