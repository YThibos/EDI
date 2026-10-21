package io.github.ythibos.edi.domain.delivery;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.TaskId;

public record Delivery(
        TaskId task,
        MergeRequestAddress address,
        boolean draft,
        String headCommit,
        MergeRequestState state,
        String landedCommit,
        Instant recordedAt,
        Instant observedAt) {

    public Delivery {
        Objects.requireNonNull(task, "A delivery is the delivery of a task.");
        Objects.requireNonNull(address, () -> "The delivery of " + task + " names its merge request.");
        Objects.requireNonNull(state, () -> "The delivery of " + task + " is open, merged or closed.");
        Objects.requireNonNull(recordedAt, () -> "The delivery of " + task + " records when EDI first saw it.");
        Objects.requireNonNull(observedAt, () -> "The delivery of " + task + " records when EDI last looked at it.");
    }

    public static Delivery of(TaskId task, MergeRequestView view, Instant at) {
        return new Delivery(task, view.address(), view.draft(), view.headCommit(), view.state(), view.landedCommit(), at, at);
    }

    public Delivery observed(MergeRequestView view, Instant at) {
        if (!view.address().equals(address)) {
            throw new IllegalArgumentException("The delivery of " + task + " is " + address + ", not " + view.address() + ".");
        }
        return new Delivery(task, address, view.draft(), view.headCommit(), view.state(), view.landedCommit(), recordedAt, at);
    }

    public boolean isClosed() {
        return state == MergeRequestState.CLOSED;
    }

    public boolean isMerged() {
        return state == MergeRequestState.MERGED;
    }

    public boolean changesWith(MergeRequestView view) {
        return view.state() != state;
    }
}
