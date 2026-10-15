package io.github.ythibos.edi.application.decision;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class AnswerDecision {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AnswerDecision(TaskStore tasks, DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public AnsweredDecision answer(TaskReference reference, GivenAnswer given, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task task = tasks.get(reference);
            Decision answered = decisionOf(task).answered(given.recordedAt(now));
            Task released = task.decisionAnswered(now);
            decisions.save(answered);
            tasks.update(released);
            recordEvents(answered, released, actor, now);
            return new AnsweredDecision(answered, released);
        });
    }

    private Decision decisionOf(Task task) {
        return decisions.ofTask(task.id()).orElseThrow(() -> new UnknownDecision(task.id()));
    }

    private void recordEvents(Decision answered, Task released, Actor actor, Instant now) {
        events.record(new DecisionAnswered(released.id(), answered.number(), answered.answer()), actor, now);
        if (released.isDone()) {
            events.record(new TaskDone(released.id()), actor, now);
        }
    }
}
