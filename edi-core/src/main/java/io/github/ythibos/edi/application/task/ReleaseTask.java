package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class ReleaseTask {

    private final TaskStore tasks;
    private final DecisionReleases releases;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ReleaseTask(TaskStore tasks, DecisionReleases releases, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.releases = releases;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Task release(TaskReference reference, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task held = tasks.get(reference);
            Task released = held.released();
            tasks.update(released);
            events.record(new TaskReleased(released.id(), held.holds().releasable()), actor, now);
            return releases.retiredOnceAnswered(released, actor, now);
        });
    }
}
