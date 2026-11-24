package io.github.ythibos.edi.domain.task;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;

public record Task(TaskId id, Intake intake, TaskStage stage, String notes, Holds holds, Instant created, Instant closed) {

    private static final String PARAGRAPH_BREAK = "\n\n";

    public Task {
        Objects.requireNonNull(holds, () -> id + " knows the reasons it waits, if only that there are none.");
        if (stage.isClosed() != (closed != null)) {
            throw new IllegalArgumentException(id + " carries a closing time exactly when it is retired or dropped.");
        }
        if (stage.isClosed() && !holds.isEmpty()) {
            throw new IllegalArgumentException(id + " is " + stage.word() + ", so it waits for nobody.");
        }
        if (intake.blockedBy().contains(id)) {
            throw new IllegalArgumentException(id + " cannot block itself.");
        }
    }

    public static Task queued(TaskId id, Intake intake, String notes, Instant at) {
        return new Task(id, intake, TaskStage.QUEUED, notes.strip(), Holds.none(), at, null);
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
            throw new Refusal(Rule.TASK_STAGE, id + " is " + stage.word() + " now, no longer " + change.from().word() + ", so it does not become "
                    + change.to().word() + ".");
        }
        return change.to().isClosed()
                ? new Task(id, intake, change.to(), notes, Holds.none(), created, at)
                : new Task(id, intake, change.to(), notes, holds, created, closed);
    }

    public Task noted(String note) {
        String paragraph = note.strip();
        if (paragraph.isEmpty()) {
            throw new IllegalArgumentException("A note on " + id + " says something.");
        }
        return new Task(id, intake, stage, notes.isEmpty() ? paragraph : notes + PARAGRAPH_BREAK + paragraph, holds, created, closed);
    }

    public Task held(Hold hold) {
        refuseWhenClosed();
        return withHolds(holds.with(hold));
    }

    public Task released() {
        if (!isHeld()) {
            throw new Refusal(Rule.TASK_HOLD, id + " is not held.");
        }
        if (holds.holdsOnlyOpenDecisions()) {
            throw new Refusal(Rule.TASK_HOLD, id + " waits only on " + holds.openDecisionsInWords() + "; answering a decision releases it.");
        }
        return withHolds(holds.released());
    }

    public Task releasedFrom(Hold hold) {
        return withHolds(holds.released(hold));
    }

    public Task decisionAnswered(DecisionNumber number) {
        return withHolds(holds.answered(number));
    }

    public void requireOneOpenDecisionNamedBy(TaskReference reference) {
        if (reference instanceof TaskReference.ById && holds.openDecisions().size() > 1) {
            throw new Refusal(Rule.TASK_HOLD, id + " waits on " + holds.openDecisionsInWords() + "; name one of them by its number.");
        }
    }

    public boolean closesOnItsAnswer() {
        return intake.kind() == TaskKind.CAPTAIN;
    }

    public Task keyedOn(WorkKey key) {
        refuseWhenClosed();
        if (intake.workKey() != null) {
            throw new Refusal(Rule.WORK_KEY, id + " already books its work on " + intake.workKey() + ".");
        }
        return new Task(id, intake.keyedOn(key), stage, notes, holds, created, closed);
    }

    public Task waitingFor(TaskId blocker) {
        refuseWhenClosed();
        if (stage != TaskStage.QUEUED) {
            throw new Refusal(Rule.TASK_STAGE, id + " is " + stage.word() + ", so it no longer waits for another task.");
        }
        return new Task(id, intake.blockedAlsoBy(blocker), stage, notes, holds, created, closed);
    }

    public Task noLongerWaitingFor(TaskId closedTask) {
        return blockedOnlyBy(intake.blockedBy().stream().filter(blocker -> !blocker.equals(closedTask)).toList());
    }

    public Task blockedOnlyBy(List<TaskId> blockers) {
        return new Task(id, intake.blockedOnlyBy(blockers), stage, notes, holds, created, closed);
    }

    public void requireFreeToWork() {
        refuseWhenClosed();
        if (intake.kind() == TaskKind.CAPTAIN) {
            throw new Refusal(Rule.TASK_STAGE, id + " is a captain item; only ships and scouts get a worker.");
        }
        if (isHeld()) {
            throw new Refusal(Rule.TASK_HOLD, id + " is held: " + reasonsItWaits() + ". Release it before dispatching it.");
        }
    }

    public void requireReadyForCleanUp() {
        if (state() != TaskState.IN_FLIGHT) {
            throw new Refusal(Rule.TASK_STAGE, id + " is " + stage.word() + ", and only work in flight is cleaned up.");
        }
        if (isHeld()) {
            throw new Refusal(Rule.TASK_HOLD, id + " is held: " + reasonsItWaits() + ". Release it before cleaning it up.");
        }
    }

    public boolean isHeld() {
        return !holds.isEmpty();
    }

    public String reasonsItWaits() {
        return String.join("; ", holds.reasons());
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

    public boolean hasWorkToResume() {
        return stage.isMergeable();
    }

    private void refuseWhenClosed() {
        if (isClosed()) {
            throw new Refusal(Rule.TASK_STAGE, id + " is " + stage.word() + ", so it waits for nobody.");
        }
    }

    private Task withHolds(Holds newHolds) {
        return new Task(id, intake, stage, notes, newHolds, created, closed);
    }
}
