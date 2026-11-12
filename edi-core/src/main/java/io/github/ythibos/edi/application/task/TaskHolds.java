package io.github.ythibos.edi.application.task;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.Holds;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class TaskHolds {

    private final DecisionStore decisions;

    public TaskHolds(DecisionStore decisions) {
        this.decisions = decisions;
    }

    public Holds of(Task task) {
        return task.isHeld() ? Holds.none().with(holdOf(task.id(), task.hold())) : Holds.none();
    }

    private Hold holdOf(TaskId task, CaptainHold hold) {
        return switch (hold.kind()) {
            case DECISION -> new Hold.OpenDecision(openDecisionOf(task));
            case DEFERRAL -> new Hold.Deferral(hold.reason(), hold.until());
            case APPROVAL -> new Hold.Approval(hold.reason());
        };
    }

    private DecisionNumber openDecisionOf(TaskId task) {
        return decisions.ofTask(task).filter(Decision::isOpen).map(Decision::number)
                .orElseThrow(() -> new IllegalStateException(task + " is held for a decision, yet none of its decisions is open."));
    }
}
