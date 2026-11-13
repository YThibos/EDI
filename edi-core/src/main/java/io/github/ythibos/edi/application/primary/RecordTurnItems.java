package io.github.ythibos.edi.application.primary;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.TurnItemStore;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.report.Turn;

@Service
public class RecordTurnItems {

    private final PrimarySessionStore sessions;
    private final TurnItemStore turnItems;
    private final TransactionTemplate transaction;

    public RecordTurnItems(PrimarySessionStore sessions, TurnItemStore turnItems, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.turnItems = turnItems;
        this.transaction = transaction;
    }

    public Turn record(UUID session, Turn items) {
        return transaction.execute(_ -> {
            requireAnOpenTurn(session);
            Turn recorded = turnItems.recordedIn(session).followedBy(items);
            turnItems.replace(session, recorded);
            return recorded;
        });
    }

    private void requireAnOpenTurn(UUID session) {
        if (sessions.get(session).map(PrimarySession::turn).isEmpty()) {
            throw new IllegalArgumentException("This Claude Code session has no turn open yet; its items are recorded once a message or a wake starts one.");
        }
    }
}
