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
import io.github.ythibos.edi.application.worker.SteerWorker.Steered;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopMissed;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class NoticeMissedStops {

    static final String CARRY_ON = "EDI could not take your stop hook when your last turn ended, and it refused your edi calls and guarded tools"
            + " while it was unreachable; it answers again now, so retry what it refused, then carry on with the task.";

    private final WorkerListQuery workerList;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final SteerWorker steer;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoticeMissedStops(WorkerListQuery workerList, WorkerStore workers, WorkerRuntime runtime, SteerWorker steer, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.workerList = workerList;
        this.workers = workers;
        this.runtime = runtime;
        this.steer = steer;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<Worker> notice() {
        List<Worker> stopped = workerList.serving().entries().stream()
                .filter(entry -> entry.health() instanceof WorkerHealth.Busy)
                .map(WorkerList.Entry::worker)
                .flatMap(worker -> missedStopOf(worker).flatMap(at -> recordedIdle(worker, at)).stream())
                .toList();
        stopped.forEach(this::wake);
        return stopped;
    }

    private Optional<Instant> missedStopOf(Worker worker) {
        return runtime.missedStopIn(worker.transcript(), worker.activitySince());
    }

    private Optional<Worker> recordedIdle(Worker read, Instant stoppedAt) {
        return transaction.execute(_ -> {
            Worker current = WorkerLookup.serving(workers, read.id());
            return isStillInTheTurnRead(current, read) ? Optional.of(idle(current, stoppedAt)) : Optional.empty();
        });
    }

    private static boolean isStillInTheTurnRead(Worker current, Worker read) {
        return current.activity() == read.activity() && current.activitySince().equals(read.activitySince());
    }

    private Worker idle(Worker worker, Instant stoppedAt) {
        Worker idle = worker.turnEnded(stoppedAt);
        workers.update(idle);
        return idle;
    }

    private void wake(Worker idle) {
        Steered steered = steer.steer(new TaskReference.ById(idle.id().task()), CARRY_ON, null, Actor.CORE);
        boolean typed = steered.delivery() == SteerDelivery.TYPED_INTO_THE_IDLE_PROMPT;
        transaction.executeWithoutResult(_ -> events.record(new WorkerStopMissed(idle.id().task(), idle.id(), idle.activitySince(),
                steered.message().number(), typed), Actor.CORE, clock.now()));
    }
}
