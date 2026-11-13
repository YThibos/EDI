package io.github.ythibos.edi.application.primary;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.TurnItemStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.report.Turn;

@Service
public class RecordTurnItems {

    private final PrimarySessionStore sessions;
    private final TurnItemStore turnItems;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecordTurnItems(PrimarySessionStore sessions, TurnItemStore turnItems, Clock clock, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.turnItems = turnItems;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Turn record(UUID session, Turn items) {
        return transaction.execute(_ -> {
            requireAnOpenTurn(session);
            turnItems.add(session, items, clock.now());
            return turnItems.recordedIn(session);
        });
    }

    private void requireAnOpenTurn(UUID session) {
        if (sessions.get(session).map(PrimarySession::turn).isEmpty()) {
            throw new IllegalArgumentException("This Claude Code session has no turn open yet; its items are recorded once a message or a wake starts one.");
        }
    }
}
