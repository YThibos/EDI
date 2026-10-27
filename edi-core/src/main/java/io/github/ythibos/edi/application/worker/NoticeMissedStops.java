package io.github.ythibos.edi.application.worker;

import java.time.Duration;
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
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
import io.github.ythibos.edi.domain.worker.ErrorStop;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class NoticeMissedStops {

    static final String CARRY_ON = "Your stop hook went unanswered when your last turn ended, and your edi calls and guarded tools were refused while the core"
            + " was unreachable; it answers again now, so retry what was refused, then carry on with the task.";
    static final Duration HOOK_GRACE = Duration.ofMinutes(2);

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
        Optional<LimitStop> limitStop = limitStopOf(worker);
        if (limitStop.isPresent()) {
            return recordedInTheTurnRead(worker, current -> parked(current, limitStop.get()));
        }
        Optional<ErrorStop> errorStop = errorStopPastTheHookGraceOf(worker);
        if (errorStop.isPresent()) {
            return recordedInTheTurnRead(worker, current -> stoppedOnAnError(current, errorStop.get())).map(this::woken);
        }
        return missedStopOf(worker).flatMap(at -> recordedInTheTurnRead(worker, current -> idle(current, at))).map(this::woken);
    }

    private Optional<LimitStop> limitStopOf(Worker worker) {
        return runtime.usageLimitStopIn(worker.transcript(), worker.activitySince());
    }

    private Optional<Worker> recordedInTheTurnRead(Worker read, UnaryOperator<Worker> stop) {
        return transaction.execute(_ -> {
            Worker current = WorkerLookup.serving(workers, read.id());
            return current.isStillInTheTurnOf(read) ? Optional.of(stop.apply(current)) : Optional.empty();
        });
    }

    private Worker parked(Worker worker, LimitStop stop) {
        Worker parked = worker.parkedOn(stop.limit(), clock.now());
        workers.update(parked);
        events.record(new UsageLimitReached(parked.id().task(), parked.id(), stop.limit()), Actor.CORE, stop.endedAt());
        return parked;
    }

    private Optional<ErrorStop> errorStopPastTheHookGraceOf(Worker worker) {
        return runtime.errorStopIn(worker.transcript(), worker.activitySince()).filter(this::isPastTheHookGrace);
    }

    private boolean isPastTheHookGrace(ErrorStop stop) {
        return !clock.now().isBefore(stop.endedAt().plus(HOOK_GRACE));
    }

    private Worker stoppedOnAnError(Worker worker, ErrorStop stop) {
        Worker idle = worker.turnEnded(stop.endedAt());
        workers.update(idle);
        events.record(new WorkerStoppedOnAnError(idle.id().task(), idle.id(), stop.error()), Actor.CORE, stop.endedAt());
        return idle;
    }

    private Worker woken(Worker idle) {
        Steered steered = steer.steer(new TaskReference.ById(idle.id().task()), CARRY_ON, null, Actor.CORE);
        transaction.executeWithoutResult(_ -> events.record(new WorkerStopMissed(idle.id().task(), idle.id(), idle.activitySince(),
                steered.message().number(), steered.delivery().wasTyped()), Actor.CORE, clock.now()));
        return idle;
    }

    private Optional<Instant> missedStopOf(Worker worker) {
        return runtime.missedStopIn(worker.transcript(), worker.activitySince());
    }

    private Worker idle(Worker worker, Instant stoppedAt) {
        Worker idle = worker.turnEnded(stoppedAt);
        workers.update(idle);
        events.record(new WorkerTurnEnded(idle.id().task(), idle.id()), Actor.CORE, stoppedAt);
        return idle;
    }
}
