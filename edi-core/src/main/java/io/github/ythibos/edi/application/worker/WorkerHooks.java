package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerNotified;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
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
    private final WorkerRuntime runtime;
    private final CountRecoveryCost recoveryCost;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WorkerHooks(WorkerStore workers, InboxStore inbox, EventLog events, WorkerRuntime runtime, CountRecoveryCost recoveryCost, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.inbox = inbox;
        this.events = events;
        this.runtime = runtime;
        this.recoveryCost = recoveryCost;
        this.clock = clock;
        this.transaction = transaction;
    }

    public HookAnswer sessionStarted(WorkerId id, UUID session, String transcript) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            Worker worker = WorkerLookup.serving(workers, id);
            workers.update(worker.started(session, transcript, now));
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
            Worker worker = WorkerLookup.serving(workers, id);
            List<InboxMessage> waiting = unreceived(id);
            if (waiting.isEmpty()) {
                workers.update(worker.turnEnded(now));
                return HookAnswer.PROCEED;
            }
            waiting.forEach(message -> received(message, id, STOP_HOOK, now));
            return HookAnswer.blockTheStop(MESSAGE_WAITING);
        });
    }

    public HookAnswer stopFailed(WorkerId id, String error, String transcript) {
        Worker stopped = transaction.execute(_ -> WorkerLookup.serving(workers, id));
        Optional<UsageLimit> limit = runtime.usageLimitStopIn(Objects.requireNonNullElse(transcript, stopped.transcript()), stopped.launchedAt());
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            Worker worker = WorkerLookup.serving(workers, id);
            if (limit.isPresent()) {
                workers.update(worker.parkedOn(limit.get(), now));
                events.record(new UsageLimitReached(id.task(), id, limit.get()), Actor.worker(id), now);
            } else {
                workers.update(worker.turnEnded(now));
                events.record(new WorkerStoppedOnAnError(id.task(), id, Objects.requireNonNullElse(error, "unknown")), Actor.worker(id), now);
            }
        });
        return HookAnswer.PROCEED;
    }

    public HookAnswer notified(WorkerId id, String kind) {
        transaction.executeWithoutResult(_ -> events.record(new WorkerNotified(id.task(), id, Objects.requireNonNullElse(kind, "unknown")),
                Actor.worker(id), clock.now()));
        return HookAnswer.PROCEED;
    }

    public HookAnswer sessionEnded(WorkerId id, String reason) {
        Worker ended = transaction.execute(_ -> {
            Instant now = clock.now();
            Worker worker = WorkerLookup.serving(workers, id).sessionEnded(now);
            workers.update(worker);
            events.record(new WorkerSessionEnded(id.task(), id, Objects.requireNonNullElse(reason, "unknown")), Actor.worker(id), now);
            return worker;
        });
        recoveryCost.countWhenARecovery(ended);
        return HookAnswer.PROCEED;
    }

    private List<InboxMessage> unreceived(WorkerId id) {
        return inbox.unacknowledgedOf(id.task()).stream().filter(message -> !message.isReceived()).toList();
    }

    private void received(InboxMessage message, WorkerId id, String proof, Instant now) {
        inbox.save(message.received(now));
        events.record(new SteerReceived(id.task(), message.number(), proof), Actor.worker(id), now);
    }
}
