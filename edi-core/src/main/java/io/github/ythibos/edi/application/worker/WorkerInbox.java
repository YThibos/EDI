package io.github.ythibos.edi.application.worker;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerAcknowledged;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Service
public class WorkerInbox {

    static final String READ_WITH_EDI_INBOX = "edi inbox";

    private final InboxStore inbox;
    private final WorkerStore workers;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WorkerInbox(InboxStore inbox, WorkerStore workers, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.inbox = inbox;
        this.workers = workers;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<InboxMessage> read(WorkerId worker) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            return inbox.unacknowledgedOf(worker.task()).stream().map(message -> receivedNow(message, worker, now)).toList();
        });
    }

    public InboxMessage acknowledge(WorkerId worker, int number) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            InboxMessage acknowledged = inbox.get(worker.task(), number)
                    .orElseThrow(() -> new Refusal(Rule.MESSAGE_WAITS, "No message " + number + " waits for " + worker.task() + "."))
                    .acknowledged(now);
            inbox.save(acknowledged);
            settleTheQuestionItResolves(worker, acknowledged, now);
            events.record(new SteerAcknowledged(worker.task(), number), Actor.worker(worker), now);
            return acknowledged;
        });
    }

    private void settleTheQuestionItResolves(WorkerId id, InboxMessage acknowledged, Instant now) {
        workers.get(id).filter(not(Worker::isRetired)).ifPresent(worker -> workers.update(worker.settledBy(acknowledged, now)));
    }

    private InboxMessage receivedNow(InboxMessage message, WorkerId worker, Instant now) {
        if (message.isReceived()) {
            return message;
        }
        InboxMessage received = message.received(now);
        inbox.save(received);
        events.record(new SteerReceived(worker.task(), message.number(), READ_WITH_EDI_INBOX), Actor.worker(worker), now);
        return received;
    }
}
