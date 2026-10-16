package io.github.ythibos.edi.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.task.TaskId;

public record Dispatch(
        TaskId task,
        CategoryId category,
        BranchName branch,
        String spec,
        ExpectedChange expectedChange,
        CaptainOverride captainsOverride,
        Instant requestedAt,
        Instant deferredAt) {

    public Dispatch {
        Objects.requireNonNull(task, "A dispatch is for a task.");
        Objects.requireNonNull(category, () -> "The dispatch of " + task + " names its category.");
        Objects.requireNonNull(branch, () -> "The dispatch of " + task + " names its branch.");
        Objects.requireNonNull(requestedAt, () -> "The dispatch of " + task + " records when it was asked for.");
        spec = Objects.requireNonNullElse(spec, "").strip();
        if (category.isMechanical() && expectedChange == null) {
            throw new Refusal("A mechanical task states its exact expected change and names every file it touches.");
        }
    }

    public static Dispatch requested(TaskId task, CategoryId category, BranchName branch, String spec,
            ExpectedChange expectedChange, CaptainOverride captainsOverride, Instant at) {
        return new Dispatch(task, category, branch, spec, expectedChange, captainsOverride, at, null);
    }

    public Dispatch deferred(Instant at) {
        return new Dispatch(task, category, branch, spec, expectedChange, captainsOverride, requestedAt, at);
    }

    public Dispatch undeferred() {
        return new Dispatch(task, category, branch, spec, expectedChange, captainsOverride, requestedAt, null);
    }

    public Dispatch asImplementationBecause(String because) {
        String handedOver = "A mechanical worker's done was refused: " + because + ".\nContinue from the isolated copy as it stands.";
        return new Dispatch(task, CategoryId.IMPLEMENTATION, branch, spec.isEmpty() ? handedOver : spec + "\n\n" + handedOver,
                null, captainsOverride, requestedAt, null);
    }
}
