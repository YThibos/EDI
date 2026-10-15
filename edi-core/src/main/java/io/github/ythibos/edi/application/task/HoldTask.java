package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class HoldTask {

    private final TaskStore tasks;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public HoldTask(TaskStore tasks, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Task hold(TaskReference reference, HoldKind kind, String reason, HoldUntil until, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task held = tasks.get(reference).held(new CaptainHold(kind, reason, until, now));
            tasks.update(held);
            events.record(new TaskHeld(held.id(), held.hold()), actor, now);
            return held;
        });
    }
}
