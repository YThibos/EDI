package io.github.ythibos.edi.application.dispatch;

import java.nio.file.Path;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

public record DispatchOutcome(Launched launched, Deferred deferred) {

    public DispatchOutcome {
        if ((launched == null) == (deferred == null)) {
            throw new IllegalArgumentException("A dispatch either launches a worker or defers the task.");
        }
    }

    public static DispatchOutcome of(Launched launched) {
        return new DispatchOutcome(launched, null);
    }

    public static DispatchOutcome of(Deferred deferred) {
        return new DispatchOutcome(null, deferred);
    }

    public boolean wasDeferred() {
        return deferred != null;
    }

    public record Launched(Worker worker, BranchName branch, Path worktree) {
    }

    public record Deferred(TaskId task, ProjectName project, int running, int capacity) {
    }
}
