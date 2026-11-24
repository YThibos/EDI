package io.github.ythibos.edi.domain.delivery;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

public record Delivery(
        TaskId task,
        MergeRequestAddress address,
        boolean draft,
        String headCommit,
        HeadChecks checks,
        MergeRequestState state,
        String landedCommit,
        String stackedOn,
        Instant recordedAt,
        Instant observedAt) {

    public Delivery {
        Objects.requireNonNull(task, "A delivery is the delivery of a task.");
        Objects.requireNonNull(address, () -> "The delivery of " + task + " names its merge request.");
        Objects.requireNonNull(state, () -> "The delivery of " + task + " is open, merged or closed.");
        Objects.requireNonNull(recordedAt, () -> "The delivery of " + task + " records when it was first seen.");
        Objects.requireNonNull(observedAt, () -> "The delivery of " + task + " records when it was last looked at.");
    }

    public static Delivery of(TaskId task, MergeRequestView view, BaseBranch base, Instant at) {
        return new Delivery(task, view.address(), view.draft(), view.headCommit(), view.checks(), view.state(), view.landedCommit(),
                view.stackedOn(base), at, at);
    }

    public Delivery observed(MergeRequestView view, BaseBranch base, Instant at) {
        if (!view.address().equals(address)) {
            throw new IllegalArgumentException("The delivery of " + task + " is " + address + ", not " + view.address() + ".");
        }
        return new Delivery(task, address, view.draft(), view.headCommit(), view.checks(), view.state(), view.landedCommit(),
                view.stackedOn(base), recordedAt, at);
    }

    public boolean checksPassed() {
        return checks == HeadChecks.PASSED;
    }

    public boolean checksFailed() {
        return checks == HeadChecks.FAILED;
    }

    public boolean isOpen() {
        return state == MergeRequestState.OPEN;
    }

    public boolean isClosed() {
        return state == MergeRequestState.CLOSED;
    }

    public boolean isMerged() {
        return state == MergeRequestState.MERGED;
    }

    public boolean isStacked() {
        return stackedOn != null;
    }

    public boolean isMergedElsewhere() {
        return isMerged() && isStacked();
    }

    public boolean isLanded() {
        return isMerged() && !isStacked();
    }

    public boolean changesWith(MergeRequestView view) {
        return view.state() != state;
    }
}
