package io.github.ythibos.edi.application.supervision;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeHeldBack;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.NoticeKind;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.domain.worker.WorkerNotice;

@Service
public class ResumeAfterUsageLimits {

    static final Duration STAGGER = Duration.ofMinutes(1);
    static final String RESUME = OperationalText.typed("Continue the task from where you stopped; run edi inbox first");

    private final WorkerListQuery workers;
    private final WorkerRuntime runtime;
    private final NoticeStore notices;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ResumeAfterUsageLimits(WorkerListQuery workers, WorkerRuntime runtime, NoticeStore notices, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.runtime = runtime;
        this.notices = notices;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Optional<Worker> resumeTheNextOneDue() {
        if (aResumeWasTypedWithin(STAGGER)) {
            return Optional.empty();
        }
        Optional<Worker> due = nextDue();
        due.ifPresent(this::resume);
        return due;
    }

    private boolean aResumeWasTypedWithin(Duration stagger) {
        Instant now = clock.now();
        return transaction.execute(_ -> notices.latestNoticed(NoticeKind.LIMIT_RESUMED)).filter(last -> now.isBefore(last.plus(stagger))).isPresent();
    }

    private Optional<Worker> nextDue() {
        Instant now = clock.now();
        return workers.serving().entries().stream()
                .filter(entry -> entry.health() instanceof WorkerHealth.LimitParked parked && parked.limit().hasResetBy(now))
                .map(WorkerList.Entry::worker)
                .filter(worker -> !Boolean.TRUE.equals(transaction.execute(_ -> wasHandled(worker))))
                .findFirst();
    }

    private boolean wasHandled(Worker worker) {
        Instant parkedAt = worker.limitPark().parkedAt();
        return notices.wasNoticed(worker.id(), new WorkerNotice(NoticeKind.LIMIT_RESUMED, parkedAt))
                || notices.wasNoticed(worker.id(), new WorkerNotice(NoticeKind.LIMIT_HELD_BACK, parkedAt));
    }

    private void resume(Worker worker) {
        PaneState pane = runtime.peek(worker.window()).state();
        if (pane.acceptsALine()) {
            runtime.press(worker.window(), Keystroke.line(RESUME));
        }
        transaction.executeWithoutResult(_ -> recordTheResume(worker, pane));
    }

    private void recordTheResume(Worker worker, PaneState pane) {
        Instant now = clock.now();
        Instant parkedAt = worker.limitPark().parkedAt();
        if (pane.acceptsALine()) {
            notices.notice(worker.id(), new WorkerNotice(NoticeKind.LIMIT_RESUMED, parkedAt), now);
            events.record(new LimitResumeTyped(worker.id().task(), worker.id()), Actor.CORE, now);
        } else if (pane == PaneState.BUSY) {
            notices.notice(worker.id(), new WorkerNotice(NoticeKind.LIMIT_RESUMED, parkedAt), now);
        } else {
            notices.notice(worker.id(), new WorkerNotice(NoticeKind.LIMIT_HELD_BACK, parkedAt), now);
            events.record(new LimitResumeHeldBack(worker.id().task(), worker.id(), pane), Actor.CORE, now);
        }
    }
}
