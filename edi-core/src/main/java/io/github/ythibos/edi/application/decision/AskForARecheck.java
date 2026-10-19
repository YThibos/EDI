package io.github.ythibos.edi.application.decision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.RecheckAsked;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class AskForARecheck {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AskForARecheck(TaskStore tasks, DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Decision ask(TaskReference reference, String note, Actor actor) {
        return transaction.execute(_ -> {
            Task task = tasks.get(reference);
            Decision open = openDecisionOf(task);
            events.record(new RecheckAsked(task.id(), open.number(), note), actor, clock.now());
            return open;
        });
    }

    private Decision openDecisionOf(Task task) {
        Decision decision = decisions.ofTask(task.id()).orElseThrow(() -> new UnknownDecision(task.id()));
        if (!decision.isOpen()) {
            throw new Refusal(decision.number() + " is already answered: " + decision.answer().verbatim());
        }
        return decision;
    }
}
