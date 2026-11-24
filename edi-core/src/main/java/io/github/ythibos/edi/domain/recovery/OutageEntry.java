package io.github.ythibos.edi.domain.recovery;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.Hold;
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

    public Hold.Outage hold() {
        return new Hold.Outage(number, "lost when " + cause.sentence(), listedAt);
    }

    public boolean holds(Task held) {
        return held.holds().held().stream().anyMatch(hold()::repeats);
    }

    public List<Hold> otherHoldsOf(Task held) {
        return held.holds().held().stream().filter(not(hold()::repeats)).toList();
    }

    public Task withoutItsHold(Task held) {
        return holds(held) ? held.releasedFrom(hold()) : held;
    }
}
