package io.github.ythibos.edi.application.worker;

import static java.util.function.Predicate.not;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
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
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Service
public class StopWorker {

    static final Duration GRACE = Duration.ofSeconds(10);

    private final EdiHome home;
    private final TaskStore tasks;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final ProcessTable processes;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public StopWorker(EdiHome home, TaskStore tasks, WorkerStore workers, WorkerRuntime runtime, ProcessTable processes, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.workers = workers;
        this.runtime = runtime;
        this.processes = processes;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public StoppedWorker stop(TaskReference reference, Actor actor) {
        Task task = transaction.execute(_ -> tasks.get(reference));
        Worker worker = transaction.execute(_ -> WorkerLookup.servingTask(workers, task.id()));
        requireItsOwnProcess(worker);
        List<Long> tree = processes.treeOf(worker.window().panePid());
        processes.stopTree(worker.window().panePid(), GRACE);
        List<Long> leftBehind = leftBehindIn(copyOf(task), tree, worker);
        processes.stopEach(leftBehind, GRACE);
        return new StoppedWorker(recordedStopped(worker, actor), leftBehind);
    }

    private Path copyOf(Task task) {
        return home.worktreeOf(task.intake().projects().getFirst(), task.id());
    }

    private List<Long> leftBehindIn(Path copy, List<Long> signalled, Worker stopping) {
        Set<Long> spared = new HashSet<>(signalled);
        spared.addAll(processes.ancestryOf(processes.ownId()));
        transaction.execute(_ -> workers.serving()).forEach(serving -> spared.addAll(sparedFor(serving, stopping)));
        return processes.heldIn(copy).stream().filter(not(spared::contains)).toList();
    }

    private List<Long> sparedFor(Worker serving, Worker stopping) {
        List<Long> spared = new ArrayList<>(List.of(serving.window().serverPid()));
        if (!serving.id().equals(stopping.id())) {
            spared.addAll(processes.treeOf(serving.window().panePid()));
        }
        return spared;
    }

    private Worker recordedStopped(Worker worker, Actor actor) {
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
        if (!processes.isStillThePaneOf(window)) {
            throw new Refusal("Process " + window.panePid() + " no longer belongs to " + worker.id()
                    + "'s tmux server, and only recorded processes are signalled.");
        }
    }

    public record StoppedWorker(Worker worker, List<Long> leftBehind) {

        public StoppedWorker {
            leftBehind = List.copyOf(leftBehind);
        }
    }
}
