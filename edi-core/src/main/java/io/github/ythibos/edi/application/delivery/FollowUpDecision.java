package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.FollowUpHeldBack;
import io.github.ythibos.edi.domain.landing.WorkAfterTheMergedHead;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskState;

@Component
public class FollowUpDecision {

    private final RaiseDecision raiseDecision;
    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final TransactionTemplate transaction;

    public FollowUpDecision(RaiseDecision raiseDecision, TaskStore tasks, DecisionStore decisions, EventLog events, TransactionTemplate transaction) {
        this.raiseDecision = raiseDecision;
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.transaction = transaction;
    }

    public void askAbout(Task merged, WorkAfterTheMergedHead work, Instant now) {
        Optional<String> waitingOn = whatHolds(merged);
        if (waitingOn.isPresent()) {
            events.record(new FollowUpHeldBack(merged.id(), work.commits(), waitingOn.get()), Actor.CORE, now);
        } else {
            raise(work);
        }
    }

    public List<FollowUpHeldBack> heldBackOfFreeTasks() {
        return transaction.execute(_ -> tasks.inState(TaskState.IN_FLIGHT).stream()
                .filter(task -> task.stage() == TaskStage.MERGED)
                .filter(task -> whatHolds(task).isEmpty())
                .filter(task -> decisions.everyOneOfTask(task.id()).stream().noneMatch(WorkAfterTheMergedHead::isTheFollowUp))
                .flatMap(task -> events.latestFollowUpHeldBackOf(task.id()).stream())
                .toList());
    }

    public void raise(WorkAfterTheMergedHead work) {
        raiseDecision.raise(new TaskReference.ById(work.task()), work.card(), Actor.CORE);
    }

    private Optional<String> whatHolds(Task task) {
        return decisions.ofTask(task.id()).filter(Decision::isOpen).map(open -> "decision " + open.number() + " is open")
                .or(() -> Optional.ofNullable(task.hold()).map(CaptainHold::reason));
    }
}
