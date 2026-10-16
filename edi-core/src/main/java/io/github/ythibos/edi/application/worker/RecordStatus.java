package io.github.ythibos.edi.application.worker;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class RecordStatus {

    private final WorkerStore workers;
    private final EventLog events;
    private final PersonalConfiguration personal;
    private final CaptainCheckouts checkouts;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecordStatus(WorkerStore workers, EventLog events, PersonalConfiguration personal, CaptainCheckouts checkouts, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.events = events;
        this.personal = personal;
        this.checkouts = checkouts;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Worker report(WorkerId id, StatusState state, StatusKey key, Instant until, String text) {
        StatusReport report = StatusReport.fromWorker(state, key, until, text, personal.address());
        Worker reported = transaction.execute(_ -> recorded(id, report));
        if (reported.doneCheck() == DoneCheck.ACCEPTED) {
            checkouts.deliverWhenLocal(id.task());
        }
        return reported;
    }

    private Worker recorded(WorkerId id, StatusReport report) {
        Instant now = clock.now();
        Worker reported = WorkerLookup.serving(workers, id).reported(report, now);
        workers.update(reported);
        events.record(new StatusReported(id.task(), id, report), Actor.worker(id), now);
        return reported;
    }
}
