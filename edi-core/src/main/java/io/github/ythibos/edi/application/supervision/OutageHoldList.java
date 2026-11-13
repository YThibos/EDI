package io.github.ythibos.edi.application.supervision;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.NoticeStore;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.NoticeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerNotice;

@Service
public class OutageHoldList {

    private final OutageStore outages;
    private final TaskStore tasks;
    private final NoticeStore notices;
    private final EventLog events;
    private final WorkerListQuery workers;
    private final LeaveTheOutageList leaving;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OutageHoldList(OutageStore outages, TaskStore tasks, NoticeStore notices, EventLog events, WorkerListQuery workers, LeaveTheOutageList leaving,
            Clock clock, TransactionTemplate transaction) {
        this.outages = outages;
        this.tasks = tasks;
        this.notices = notices;
        this.events = events;
        this.workers = workers;
        this.leaving = leaving;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<OutageEntry> holdTheDead() {
        return hold(DeadWorker.among(workers.serving()));
    }

    List<OutageEntry> hold(List<DeadWorker> dead) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            leaving.everyTaskWithNothingLeftToResume(now);
            dead.stream().filter(this::isWorthListing).forEach(lost -> list(lost, now));
            return outages.open();
        });
    }

    private boolean isWorthListing(DeadWorker lost) {
        TaskId task = lost.worker().id().task();
        return outages.holding(task).isEmpty() && tasks.get(task).hasWorkToResume();
    }

    private void list(DeadWorker lost, Instant now) {
        Worker worker = lost.worker();
        OutageEntry entry = new OutageEntry(OutageEntry.nextNumberAfter(outages.open()), worker.id().task(), worker.id(), lost.cause(), now);
        outages.list(entry);
        noticeTheDeath(worker, now);
        holdTheTask(entry, now);
        events.record(new LostInOutage(entry.task(), worker.id(), entry.number(), lost.cause()), Actor.CORE, now);
    }

    private void noticeTheDeath(Worker worker, Instant now) {
        WorkerNotice died = new WorkerNotice(NoticeKind.DIED, worker.launchedAt());
        if (!notices.wasNoticed(worker.id(), died)) {
            notices.notice(worker.id(), died, now);
        }
    }

    private void holdTheTask(OutageEntry entry, Instant now) {
        Task task = tasks.get(entry.task());
        if (!task.isHeld()) {
            Task held = task.held(entry.hold());
            tasks.update(held);
            events.record(new TaskHeld(held.id(), held.hold()), Actor.CORE, now);
        }
    }
}
