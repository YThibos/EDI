package io.github.ythibos.edi.domain.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.domain.status.StatusState;

public record SupervisionRules(Duration staleAfter, Duration pauseResurface, Duration busyCeiling) {

    public static final Duration STALE_AFTER = Duration.ofMinutes(20);
    public static final Duration PAUSE_RESURFACE = Duration.ofHours(4);
    public static final Duration BUSY_CEILING = Duration.ofHours(3);
    private static final Set<StatusState> AWAITING_THE_PRIMARY =
            EnumSet.of(StatusState.DONE, StatusState.NEEDS_DECISION, StatusState.BLOCKED, StatusState.FAILED, StatusState.PAUSED);

    public SupervisionRules {
        Objects.requireNonNull(staleAfter, "Supervision knows when an idle worker has waited too long.");
        Objects.requireNonNull(pauseResurface, "Supervision knows when an open-ended pause resurfaces.");
        Objects.requireNonNull(busyCeiling, "Supervision knows when a busy worker has been busy too long.");
    }

    public WorkerHealth healthOf(Worker worker, WindowCheck window, BootId currentBoot, Instant now) {
        if (worker.stopRequestedAt() != null) {
            return new WorkerHealth.Stopped(worker.stopRequestedAt());
        }
        if (worker.launch().happenedBefore(currentBoot)) {
            return new WorkerHealth.Dead(DeathCause.MACHINE_RESTARTED);
        }
        if (!window.isOpen()) {
            return worker.handedItsWorkOver() ? new WorkerHealth.Finished() : new WorkerHealth.Dead(DeathCause.of(window));
        }
        if (worker.limitPark() != null) {
            return new WorkerHealth.LimitParked(worker.limitPark().limit());
        }
        return activityHealthOf(worker, now);
    }

    public Optional<WorkerNotice> noticeDue(Worker worker, WorkerHealth health, boolean messageWaiting, Instant now) {
        return switch (health) {
            case WorkerHealth.Dead _ -> Optional.of(new WorkerNotice(NoticeKind.DIED, worker.launchedAt()));
            case WorkerHealth.Busy busy -> busyTooLong(busy, now);
            case WorkerHealth.Idle _ -> waitEnded(worker, now).or(() -> stale(worker, messageWaiting, now));
            case WorkerHealth.Starting _, WorkerHealth.Waiting _, WorkerHealth.LimitParked _, WorkerHealth.Stopped _,
                    WorkerHealth.Finished _ -> Optional.empty();
        };
    }

    private WorkerHealth activityHealthOf(Worker worker, Instant now) {
        return switch (worker.activity()) {
            case STARTING -> new WorkerHealth.Starting(worker.activitySince());
            case BUSY -> new WorkerHealth.Busy(worker.activitySince());
            case IDLE -> declaredWaitEnd(worker)
                    .filter(now::isBefore)
                    .<WorkerHealth>map(WorkerHealth.Waiting::new)
                    .orElseGet(() -> new WorkerHealth.Idle(worker.activitySince()));
        };
    }

    private Optional<Instant> declaredWaitEnd(Worker worker) {
        if (!worker.lastReported(StatusState.PAUSED)) {
            return Optional.empty();
        }
        Worker.ReportedStatus paused = worker.lastStatus();
        return Optional.of(Objects.requireNonNullElse(paused.report().until(), paused.at().plus(pauseResurface)));
    }

    private Optional<WorkerNotice> busyTooLong(WorkerHealth.Busy busy, Instant now) {
        return now.isBefore(busy.since().plus(busyCeiling)) ? Optional.empty() : Optional.of(new WorkerNotice(NoticeKind.BUSY_TOO_LONG, busy.since()));
    }

    private Optional<WorkerNotice> waitEnded(Worker worker, Instant now) {
        return declaredWaitEnd(worker)
                .filter(end -> !now.isBefore(end))
                .map(_ -> new WorkerNotice(NoticeKind.WAIT_ENDED, worker.lastStatus().at()));
    }

    private Optional<WorkerNotice> stale(Worker worker, boolean messageWaiting, Instant now) {
        boolean awaitsThePrimary = worker.lastStatus() != null && AWAITING_THE_PRIMARY.contains(worker.lastStatus().report().state());
        Instant quietSince = worker.quietSince();
        if (awaitsThePrimary || messageWaiting || now.isBefore(quietSince.plus(staleAfter))) {
            return Optional.empty();
        }
        return Optional.of(new WorkerNotice(NoticeKind.STALE, quietSince));
    }
}
