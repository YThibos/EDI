package io.github.ythibos.edi.domain.task;

import java.time.Instant;

import io.github.ythibos.edi.domain.Refusal;

public record Task(TaskId id, Intake intake, TaskState state, String notes, CaptainHold hold, Instant created, Instant closed) {

    private static final String PARAGRAPH_BREAK = "\n\n";

    public Task {
        if (isDone(state) != (closed != null)) {
            throw new IllegalArgumentException(id + " carries a closing time exactly when it is done.");
        }
        if (isDone(state) && hold != null) {
            throw new IllegalArgumentException(id + " is done, so it waits for nobody.");
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
        refuseWhenDone();
        if (newHold.awaitsADecision()) {
            throw new Refusal("A decision holds " + id + " when it is raised with its card.");
        }
        if (isHeld()) {
            throw new Refusal(id + " is already held: " + hold.reason() + ". Release it first.");
        }
        return withHold(newHold);
    }

    public Task heldForDecision(CaptainHold decisionHold) {
        refuseWhenDone();
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

    public boolean isHeld() {
        return hold != null;
    }

    public boolean isDone() {
        return isDone(state);
    }

    private static boolean isDone(TaskState state) {
        return state == TaskState.DONE;
    }

    private void refuseWhenDone() {
        if (isDone()) {
            throw new Refusal(id + " is done, so it waits for nobody.");
        }
    }

    private Task withHold(CaptainHold newHold) {
        return new Task(id, intake, state, notes, newHold, created, closed);
    }
}
