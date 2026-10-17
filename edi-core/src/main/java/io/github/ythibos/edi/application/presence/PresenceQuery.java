package io.github.ythibos.edi.application.presence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.presence.Presence;

@Service
public class PresenceQuery {

    private final PresenceStore presence;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PresenceQuery(PresenceStore presence, Clock clock, TransactionTemplate transaction) {
        this.presence = presence;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Presence current() {
        return transaction.execute(_ -> presence.current()).orElseGet(() -> Presence.presentSince(clock.now()));
    }
}
