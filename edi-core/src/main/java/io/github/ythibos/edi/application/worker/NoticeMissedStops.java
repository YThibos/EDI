package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.SteerWorker.Steered;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopMissed;
import io.github.ythibos.edi.domain.worker.UsageLimit;
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
        return workerList.serving().entries().stream()
                .filter(entry -> entry.health() instanceof WorkerHealth.Busy)
                .map(WorkerList.Entry::worker)
                .flatMap(worker -> missedStopRecorded(worker).stream())
                .toList();
    }

    private Optional<Worker> missedStopRecorded(Worker worker) {
        Optional<UsageLimit> limit = usageLimitOf(worker);
        if (limit.isPresent()) {
            return recordedInTheTurnRead(worker, current -> parked(current, limit.get()));
        }
        return missedStopOf(worker).flatMap(at -> recordedInTheTurnRead(worker, current -> idle(current, at))).map(this::woken);
    }

    private Optional<UsageLimit> usageLimitOf(Worker worker) {
        return runtime.usageLimitStopIn(worker.transcript(), worker.activitySince());
    }

    private Optional<Worker> recordedInTheTurnRead(Worker read, UnaryOperator<Worker> stop) {
        return transaction.execute(_ -> {
            Worker current = WorkerLookup.serving(workers, read.id());
            return current.isStillInTheTurnOf(read) ? Optional.of(stop.apply(current)) : Optional.empty();
        });
    }

    private Worker parked(Worker worker, UsageLimit limit) {
        Instant now = clock.now();
        Worker parked = worker.parkedOn(limit, now);
        workers.update(parked);
        events.record(new UsageLimitReached(parked.id().task(), parked.id(), limit), Actor.CORE, now);
        return parked;
    }

    private Optional<Instant> missedStopOf(Worker worker) {
        return runtime.missedStopIn(worker.transcript(), worker.activitySince());
    }

    private Worker idle(Worker worker, Instant stoppedAt) {
        Worker idle = worker.turnEnded(stoppedAt);
        workers.update(idle);
        return idle;
    }

    private Worker woken(Worker idle) {
        Steered steered = steer.steer(new TaskReference.ById(idle.id().task()), CARRY_ON, null, Actor.CORE);
        transaction.executeWithoutResult(_ -> events.record(new WorkerStopMissed(idle.id().task(), idle.id(), idle.activitySince(),
                steered.message().number(), steered.delivery().wasTyped()), Actor.CORE, clock.now()));
        return idle;
    }
}
