package io.github.ythibos.edi.domain.task;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.WorkKey;

public record Task(TaskId id, Intake intake, TaskState state, String notes, CaptainHold hold, Instant created, Instant closed) {

    private static final String PARAGRAPH_BREAK = "\n\n";

    public Task {
        if (state.isClosed() != (closed != null)) {
            throw new IllegalArgumentException(id + " carries a closing time exactly when it is done or dropped.");
        }
        if (state.isClosed() && hold != null) {
            throw new IllegalArgumentException(id + " is " + state.word() + ", so it waits for nobody.");
        }
        if (intake.blockedBy().contains(id)) {
            throw new IllegalArgumentException(id + " cannot block itself.");
        }
    }

    public static Task queued(TaskId id, Intake intake, String notes, Instant at) {
        return new Task(id, intake, TaskState.QUEUED, notes.strip(), null, at, null);
    }

    public Task noted(String note) {
        String paragraph = note.strip();
        if (paragraph.isEmpty()) {
            throw new IllegalArgumentException("A note on " + id + " says something.");
        }
        return new Task(id, intake, state, notes.isEmpty() ? paragraph : notes + PARAGRAPH_BREAK + paragraph, hold, created, closed);
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

    public Task decisionAnswered(Instant at) {
        if (!isHeld() || !hold.awaitsADecision()) {
            throw new Refusal(id + " is not held for a decision.");
        }
        return intake.kind() == TaskKind.CAPTAIN
                ? new Task(id, intake, TaskState.DONE, notes, null, created, at)
                : withHold(null);
    }

    public Task keyedOn(WorkKey key) {
        refuseWhenClosed();
        if (intake.workKey() != null) {
            throw new Refusal(id + " already books its work on " + intake.workKey() + ".");
        }
        return new Task(id, intake.keyedOn(key), state, notes, hold, created, closed);
    }

    public Task waitingFor(TaskId blocker) {
        refuseWhenClosed();
        if (state != TaskState.QUEUED) {
            throw new Refusal(id + " is " + state.word() + ", so it no longer waits for another task.");
        }
        return new Task(id, intake.blockedAlsoBy(blocker), state, notes, hold, created, closed);
    }

    public Task noLongerWaitingFor(TaskId closedTask) {
        return blockedOnlyBy(intake.blockedBy().stream().filter(blocker -> !blocker.equals(closedTask)).toList());
    }

    public Task blockedOnlyBy(List<TaskId> blockers) {
        return new Task(id, intake.blockedOnlyBy(blockers), state, notes, hold, created, closed);
    }

    public Task dispatched() {
        refuseWhenClosed();
        if (intake.kind() == TaskKind.CAPTAIN) {
            throw new Refusal(id + " is a captain item; only ships and scouts get a worker.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is held: " + hold.reason() + ". Release it before dispatching it.");
        }
        return state == TaskState.IN_FLIGHT ? this : new Task(id, intake, TaskState.IN_FLIGHT, notes, hold, created, closed);
    }

    public Task cleanedUp(Instant at) {
        requireReadyForCleanUp();
        return new Task(id, intake, TaskState.DONE, notes, null, created, at);
    }

    public void requireReadyForCleanUp() {
        if (state != TaskState.IN_FLIGHT) {
            throw new Refusal(id + " is " + state.word() + ", and only work in flight is cleaned up.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is held: " + hold.reason() + ". Release it before cleaning it up.");
        }
    }

    public Task dropped(CaptainsWord word, Instant at) {
        Objects.requireNonNull(word, () -> id + " is dropped only on the captain's word.");
        if (state == TaskState.DROPPED) {
            throw new Refusal(id + " is already dropped.");
        }
        return new Task(id, intake, TaskState.DROPPED, notes, null, created, at);
    }

    public boolean isHeld() {
        return hold != null;
    }

    public boolean isDone() {
        return state == TaskState.DONE;
    }

    public boolean isClosed() {
        return state.isClosed();
    }

    public boolean hasLanded() {
        return state == TaskState.DONE;
    }

    private void refuseWhenClosed() {
        if (isClosed()) {
            throw new Refusal(id + " is " + state.word() + ", so it waits for nobody.");
        }
    }

    private Task withHold(CaptainHold newHold) {
        return new Task(id, intake, state, notes, newHold, created, closed);
    }
}
