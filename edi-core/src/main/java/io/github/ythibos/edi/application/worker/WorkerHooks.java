package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class WorkerHooks {

    static final String TYPED_NOTICE = "typed notice";
    static final String STOP_HOOK = "stop hook";
    private static final String MESSAGE_WAITING = "A message is waiting: run edi inbox, then act on it before you stop.";

    private final WorkerStore workers;
    private final InboxStore inbox;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WorkerHooks(WorkerStore workers, InboxStore inbox, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.inbox = inbox;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public HookAnswer sessionStarted(WorkerId id) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            Worker worker = WorkerLookup.serving(workers, id);
            workers.update(worker.started(now));
            if (worker.startedAt() == null) {
                events.record(new WorkerStarted(id.task(), id), Actor.worker(id), now);
            }
        });
        return HookAnswer.PROCEED;
    }

    public HookAnswer promptSubmitted(WorkerId id, String prompt) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            workers.update(WorkerLookup.serving(workers, id).promptSubmitted(now));
            unreceived(id).stream().filter(message -> message.isNamedIn(prompt)).forEach(message -> received(message, id, TYPED_NOTICE, now));
        });
        return HookAnswer.PROCEED;
    }

    public HookAnswer stopping(WorkerId id) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            workers.update(WorkerLookup.serving(workers, id).stopped());
            List<InboxMessage> waiting = unreceived(id);
            waiting.forEach(message -> received(message, id, STOP_HOOK, now));
            return waiting.isEmpty() ? HookAnswer.PROCEED : HookAnswer.blockTheStop(MESSAGE_WAITING);
        });
    }

    private List<InboxMessage> unreceived(WorkerId id) {
        return inbox.unacknowledgedOf(id.task()).stream().filter(message -> !message.isReceived()).toList();
    }

    private void received(InboxMessage message, WorkerId id, String proof, Instant now) {
        inbox.save(message.received(now));
        events.record(new SteerReceived(id.task(), message.number(), proof), Actor.worker(id), now);
    }
}
