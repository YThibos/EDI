package io.github.ythibos.edi.application.dispatch;

import java.nio.file.Path;
import java.util.UUID;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;

public record DispatchOutcome(Launched launched, Deferred deferred, TaskId plannedShip) {

    public DispatchOutcome {
        if ((launched == null) == (deferred == null)) {
            throw new IllegalArgumentException("A dispatch either launches a worker or defers the task.");
        }
    }

    public static DispatchOutcome of(Launched launched) {
        return new DispatchOutcome(launched, null, null);
    }

    public static DispatchOutcome of(Deferred deferred) {
        return new DispatchOutcome(null, deferred, null);
    }

    public DispatchOutcome planning(TaskId ship) {
        return new DispatchOutcome(launched, deferred, ship);
    }

    public boolean wasDeferred() {
        return deferred != null;
    }

    public boolean plansAShip() {
        return plannedShip != null;
    }

    public record Launched(Worker worker, BranchName branch, Path worktree, UnresumedSession unresumed) {
    }

    public record UnresumedSession(UUID session, String why) {

        public static UnresumedSession withoutATranscript(UUID session) {
            return new UnresumedSession(session, "left no transcript to resume");
        }

        public static UnresumedSession onTheLogin(UUID session, LoginName login) {
            return new UnresumedSession(session, "is not resumed on the Claude login " + login + " yet");
        }

        public String sentence() {
            return "session " + session + " " + why;
        }
    }

    public record Deferred(TaskId task, ProjectName project, int running, int capacity) {
    }
}
