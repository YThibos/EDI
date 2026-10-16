package io.github.ythibos.edi.application.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.inbox.InboxMessage;

@Service
public class NoticeOverdueSteers {

    static final Duration PATIENCE = Duration.ofMinutes(5);

    private final InboxStore inbox;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public NoticeOverdueSteers(InboxStore inbox, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.inbox = inbox;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<InboxMessage> notice() {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            return inbox.awaitingReceipt().stream()
                    .filter(message -> message.isOverdue(now, PATIENCE))
                    .map(message -> reportedUnreceived(message, now))
                    .toList();
        });
    }

    private InboxMessage reportedUnreceived(InboxMessage message, Instant now) {
        InboxMessage reported = message.reportedUnreceived(now);
        inbox.save(reported);
        events.record(new SteerNotReceived(message.task(), message.number()), Actor.CORE, now);
        return reported;
    }
}
