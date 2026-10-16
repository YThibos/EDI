package io.github.ythibos.edi.application.supervision;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.CountRecoveryCost;
import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DeclaredWaitEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerBusyTooLong;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.worker.Awaited;
import io.github.ythibos.edi.domain.worker.NoticeKind;
import io.github.ythibos.edi.domain.worker.SupervisionRules;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerNotice;

@Service
public class JudgeWorkers {

    private static final int AN_OUTAGE = 2;

    private final WorkerListQuery workers;
    private final InboxStore inbox;
    private final PipelineRunStore pipelineRuns;
    private final NoticeStore notices;
    private final EventLog events;
    private final OutageHoldList outageHoldList;
    private final CountRecoveryCost recoveryCost;
    private final MachineProfile machine;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public JudgeWorkers(WorkerListQuery workers, InboxStore inbox, PipelineRunStore pipelineRuns, NoticeStore notices, EventLog events,
            OutageHoldList outageHoldList, CountRecoveryCost recoveryCost, MachineProfile machine, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.inbox = inbox;
        this.pipelineRuns = pipelineRuns;
        this.notices = notices;
        this.events = events;
        this.outageHoldList = outageHoldList;
        this.recoveryCost = recoveryCost;
        this.machine = machine;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void judge() {
        WorkerList serving = workers.serving();
        List<DeadWorker> dead = DeadWorker.among(serving);
        noticeTheLiving(serving, machine.supervision());
        reportTheDead(dead);
        dead.forEach(lost -> recoveryCost.countWhenARecovery(lost.worker()));
    }

    private void noticeTheLiving(WorkerList serving, SupervisionRules rules) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            serving.entries().stream()
                    .filter(entry -> !entry.health().isDead())
                    .forEach(entry -> rules.noticeDue(entry.worker(), entry.health(), awaitedBy(entry.worker()), now)
                            .ifPresent(notice -> noticeOnce(entry.worker(), notice, now)));
        });
    }

    private void reportTheDead(List<DeadWorker> dead) {
        if (dead.size() >= AN_OUTAGE) {
            outageHoldList.hold(dead);
            return;
        }
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            dead.forEach(lost -> noticeOnce(lost.worker(), new WorkerNotice(NoticeKind.DIED, lost.worker().launchedAt()), now,
                    new WorkerDied(lost.worker().id().task(), lost.worker().id(), lost.cause())));
        });
    }

    private Awaited awaitedBy(Worker worker) {
        return new Awaited(hasAMessageWaiting(worker), hasItsPipelineRunning(worker));
    }

    private boolean hasAMessageWaiting(Worker worker) {
        return inbox.unacknowledgedOf(worker.id().task()).stream().anyMatch(message -> !message.isReceived());
    }

    private boolean hasItsPipelineRunning(Worker worker) {
        return pipelineRuns.latestOf(worker.id().task()).filter(PipelineRun::isRunning).isPresent();
    }

    private void noticeOnce(Worker worker, WorkerNotice notice, Instant now) {
        noticeOnce(worker, notice, now, eventOf(worker, notice));
    }

    private void noticeOnce(Worker worker, WorkerNotice notice, Instant now, DomainEvent event) {
        if (!notices.wasNoticed(worker.id(), notice)) {
            notices.notice(worker.id(), notice, now);
            events.record(event, Actor.CORE, now);
        }
    }

    private static DomainEvent eventOf(Worker worker, WorkerNotice notice) {
        WorkerId id = worker.id();
        return switch (notice.kind()) {
            case STALE -> new WorkerStale(id.task(), id, notice.since());
            case BUSY_TOO_LONG -> new WorkerBusyTooLong(id.task(), id, notice.since());
            case WAIT_ENDED -> new DeclaredWaitEnded(id.task(), id, worker.lastStatus().report());
            case DIED, LIMIT_RESUMED, LIMIT_HELD_BACK -> throw new IllegalArgumentException("A living worker is not noticed as " + notice.kind().word() + ".");
        };
    }
}
