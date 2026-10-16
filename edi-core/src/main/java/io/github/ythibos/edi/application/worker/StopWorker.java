package io.github.ythibos.edi.application.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopRequested;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Service
public class StopWorker {

    static final Duration GRACE = Duration.ofSeconds(10);

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final ProcessTable processes;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public StopWorker(TaskStore tasks, WorkerStore workers, WorkerRuntime runtime, ProcessTable processes, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.runtime = runtime;
        this.processes = processes;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Worker stop(TaskReference reference, Actor actor) {
        Worker worker = transaction.execute(_ -> WorkerLookup.servingTask(workers, tasks.get(reference).id()));
        requireItsOwnProcess(worker);
        processes.stopTree(worker.window().panePid(), GRACE);
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Worker stopped = WorkerLookup.serving(workers, worker.id()).stopRequested(now);
            workers.update(stopped);
            events.record(new WorkerStopRequested(worker.id().task(), worker.id()), actor, now);
            return stopped;
        });
    }

    private void requireItsOwnProcess(Worker worker) {
        WorkerWindow window = worker.window();
        if (!runtime.isRunning(window)) {
            throw new Refusal(worker.id() + " is not running, so there is nothing to stop.");
        }
        OptionalLong parent = processes.parentOf(window.panePid());
        if (parent.isEmpty() || parent.getAsLong() != window.serverPid()) {
            throw new Refusal("Process " + window.panePid() + " no longer belongs to " + worker.id()
                    + "'s tmux server, and EDI signals only processes it recorded.");
        }
    }
}
