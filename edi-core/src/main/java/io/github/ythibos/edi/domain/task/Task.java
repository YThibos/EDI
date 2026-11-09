package io.github.ythibos.edi.domain.task;

import java.time.Instant;
import java.util.List;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;

public record Task(TaskId id, Intake intake, TaskStage stage, String notes, CaptainHold hold, Instant created, Instant closed) {

    private static final String PARAGRAPH_BREAK = "\n\n";

    public Task {
        if (stage.isClosed() != (closed != null)) {
            throw new IllegalArgumentException(id + " carries a closing time exactly when it is retired or dropped.");
        }
        if (stage.isClosed() && hold != null) {
            throw new IllegalArgumentException(id + " is " + stage.word() + ", so it waits for nobody.");
        }
        if (intake.blockedBy().contains(id)) {
            throw new IllegalArgumentException(id + " cannot block itself.");
        }
    }

    public static Task queued(TaskId id, Intake intake, String notes, Instant at) {
        return new Task(id, intake, TaskStage.QUEUED, notes.strip(), null, at, null);
    }

    public TaskState state() {
        return stage.state();
    }

    public StageTransitions transitions() {
        return new StageTransitions(id, intake.kind());
    }

    public Task movedBy(StageChange change, Instant at) {
        if (!change.task().equals(id)) {
            throw new IllegalArgumentException("A change of " + change.task() + " does not move " + id + ".");
        }
        if (change.from() != stage) {
            throw new Refusal(id + " is " + stage.word() + " now, no longer " + change.from().word() + ", so it does not become " + change.to().word()
                    + ".");
        }
        return change.to().isClosed()
                ? new Task(id, intake, change.to(), notes, null, created, at)
                : new Task(id, intake, change.to(), notes, hold, created, closed);
    }

    public Task noted(String note) {
        String paragraph = note.strip();
        if (paragraph.isEmpty()) {
            throw new IllegalArgumentException("A note on " + id + " says something.");
        }
        return new Task(id, intake, stage, notes.isEmpty() ? paragraph : notes + PARAGRAPH_BREAK + paragraph, hold, created, closed);
    }

    public Task held(CaptainHold newHold) {
        refuseWhenClosed();
        if (newHold.awaitsADecision()) {
            throw new Refusal("A decision holds " + id + " when it is raised with its card.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is already held: " + hold.reason() + ". Release it first.");
        }
        return withHold(newHold);
    }

    public Task heldForDecision(CaptainHold decisionHold) {
        refuseWhenClosed();
        return withHold(decisionHold);
    }

    public Task released() {
        if (!isHeld()) {
            throw new Refusal(id + " is not held.");
        }
        if (hold.awaitsADecision()) {
            throw new Refusal(id + " waits on its decision; answering the decision releases it.");
        }
        return withHold(null);
    }

    public Task decisionAnswered() {
        if (!isHeld() || !hold.awaitsADecision()) {
            throw new Refusal(id + " is not held for a decision.");
        }
        return withHold(null);
    }

    public boolean closesOnItsAnswer() {
        return intake.kind() == TaskKind.CAPTAIN;
    }

    public Task keyedOn(WorkKey key) {
        refuseWhenClosed();
        if (intake.workKey() != null) {
            throw new Refusal(id + " already books its work on " + intake.workKey() + ".");
        }
        return new Task(id, intake.keyedOn(key), stage, notes, hold, created, closed);
    }

    public Task waitingFor(TaskId blocker) {
        refuseWhenClosed();
        if (stage != TaskStage.QUEUED) {
            throw new Refusal(id + " is " + stage.word() + ", so it no longer waits for another task.");
        }
        return new Task(id, intake.blockedAlsoBy(blocker), stage, notes, hold, created, closed);
    }

    public Task noLongerWaitingFor(TaskId closedTask) {
        return blockedOnlyBy(intake.blockedBy().stream().filter(blocker -> !blocker.equals(closedTask)).toList());
    }

    public Task blockedOnlyBy(List<TaskId> blockers) {
        return new Task(id, intake.blockedOnlyBy(blockers), stage, notes, hold, created, closed);
    }

    public void requireFreeToWork() {
        refuseWhenClosed();
        if (intake.kind() == TaskKind.CAPTAIN) {
            throw new Refusal(id + " is a captain item; only ships and scouts get a worker.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is held: " + hold.reason() + ". Release it before dispatching it.");
        }
    }

    public void requireReadyForCleanUp() {
        if (state() != TaskState.IN_FLIGHT) {
            throw new Refusal(id + " is " + stage.word() + ", and only work in flight is cleaned up.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is held: " + hold.reason() + ". Release it before cleaning it up.");
        }
    }

    public boolean isHeld() {
        return hold != null;
    }

    public boolean isDone() {
        return stage == TaskStage.RETIRED;
    }

    public boolean isClosed() {
        return stage.isClosed();
    }

    public boolean hasLanded() {
        return stage.hasLanded();
    }

    private void refuseWhenClosed() {
        if (isClosed()) {
            throw new Refusal(id + " is " + stage.word() + ", so it waits for nobody.");
        }
    }

    private Task withHold(CaptainHold newHold) {
        return new Task(id, intake, stage, notes, newHold, created, closed);
    }
}
