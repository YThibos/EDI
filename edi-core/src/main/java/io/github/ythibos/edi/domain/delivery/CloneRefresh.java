package io.github.ythibos.edi.domain.delivery;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

public record CloneRefresh(
        TaskId task,
        ProjectName project,
        BaseBranch base,
        String expectedCommit,
        Instant requestedAt,
        Instant verifiedAt,
        Instant cleanUpTriedAt) {

    public CloneRefresh {
        Objects.requireNonNull(task, "A clone refresh follows the merge of a task.");
        Objects.requireNonNull(project, () -> "The refresh after " + task + " is of a project's clone.");
        Objects.requireNonNull(base, () -> "The refresh after " + task + " looks for its commit on a base branch.");
        if (expectedCommit == null || expectedCommit.isBlank()) {
            throw new IllegalArgumentException("The refresh after " + task + " waits for the commit the merge landed.");
        }
        Objects.requireNonNull(requestedAt, () -> "The refresh after " + task + " records when it was asked for.");
        if (cleanUpTriedAt != null && verifiedAt == null) {
            throw new IllegalArgumentException("The clean-up after " + task + " is tried only once its refresh is verified.");
        }
    }

    public static CloneRefresh awaiting(TaskId task, ProjectName project, BaseBranch base, String expectedCommit, Instant at) {
        return new CloneRefresh(task, project, base, expectedCommit, at, null, null);
    }

    public CloneRefresh verified(Instant at) {
        return new CloneRefresh(task, project, base, expectedCommit, requestedAt, at, null);
    }

    public CloneRefresh cleanUpTried(Instant at) {
        return new CloneRefresh(task, project, base, expectedCommit, requestedAt, verifiedAt, at);
    }

    public boolean isPending() {
        return verifiedAt == null;
    }

    public String baseRef() {
        return "origin/" + base;
    }
}
