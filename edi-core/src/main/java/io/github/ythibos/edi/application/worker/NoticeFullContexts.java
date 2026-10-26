package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.ContextLimitReached;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class NoticeFullContexts {

    private final WorkerListQuery workerList;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoticeFullContexts(WorkerListQuery workerList, WorkerStore workers, WorkerRuntime runtime, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.workerList = workerList;
        this.workers = workers;
        this.runtime = runtime;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<Worker> notice() {
        return workerList.serving().entries().stream()
                .filter(entry -> takesPrompts(entry.health()))
                .map(WorkerList.Entry::worker)
                .flatMap(worker -> fullSinceOf(worker).flatMap(since -> recordedFull(worker, since)).stream())
                .toList();
    }

    private static boolean takesPrompts(WorkerHealth health) {
        return health instanceof WorkerHealth.Busy || health instanceof WorkerHealth.Idle;
    }

    private Optional<Instant> fullSinceOf(Worker worker) {
        return runtime.contextLimitStopIn(worker.transcript(), worker.launchedAt());
    }

    private Optional<Worker> recordedFull(Worker read, Instant fullSince) {
        return transaction.execute(_ -> {
            Worker current = WorkerLookup.serving(workers, read.id());
            return current.isStillInTheTurnOf(read) ? Optional.of(filled(current, fullSince)) : Optional.empty();
        });
    }

    private Worker filled(Worker worker, Instant fullSince) {
        Worker full = worker.contextFilled(fullSince);
        workers.update(full);
        events.record(new ContextLimitReached(full.id().task(), full.id(), fullSince), Actor.CORE, clock.now());
        return full;
    }
}
