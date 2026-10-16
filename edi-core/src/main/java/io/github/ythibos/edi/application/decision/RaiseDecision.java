package io.github.ythibos.edi.application.decision;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class RaiseDecision {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RaiseDecision(TaskStore tasks, DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DecisionNumber raise(TaskReference reference, DecisionCard card, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task task = tasks.get(reference);
            Decision decision = decisionOn(task, card, now);
            tasks.update(task.heldForDecision(decision.holdOnItsTask()));
            decisions.save(decision);
            events.record(new DecisionRaised(task.id(), decision.number(), card), actor, now);
            return decision.number();
        });
    }

    private Decision decisionOn(Task task, DecisionCard card, Instant now) {
        return decisions.ofTask(task.id())
                .map(earlier -> earlier.reraised(card, now))
                .orElseGet(() -> Decision.raised(decisions.nextNumber(), task.id(), card, now));
    }
}
