package io.github.ythibos.edi.application.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerLaunchFailed;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class NoticeMissedLaunches {

    static final Duration PATIENCE = Duration.ofMinutes(2);

    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoticeMissedLaunches(WorkerStore workers, WorkerRuntime runtime, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.runtime = runtime;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<Worker> notice() {
        List<Worker> missed = transaction.execute(_ -> workers.serving().stream()
                .filter(worker -> worker.missedItsLaunch(clock.now(), PATIENCE))
                .toList());
        return missed.stream().map(this::reportedFailed).toList();
    }

    private Worker reportedFailed(Worker worker) {
        PaneState pane = runtime.isRunning(worker.window()) ? runtime.peek(worker.window()).state() : PaneState.UNKNOWN;
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Worker failed = worker.launchFailed(now);
            workers.update(failed);
            events.record(new WorkerLaunchFailed(worker.id().task(), worker.id(), pane), Actor.CORE, now);
            return failed;
        });
    }
}
