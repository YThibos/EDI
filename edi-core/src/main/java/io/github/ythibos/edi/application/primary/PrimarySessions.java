package io.github.ythibos.edi.application.primary;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.domain.primary.PrimaryRole;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.Standing;

@Component
public class PrimarySessions {

    private final PrimarySessionStore sessions;
    private final ProcessTable processes;
    private final TransactionTemplate transaction;

    public PrimarySessions(PrimarySessionStore sessions, ProcessTable processes, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.processes = processes;
        this.transaction = transaction;
    }

    public Standing standingOf(UUID session) {
        return transaction.execute(_ -> role().standingOf(session));
    }

    public Optional<PrimarySession> holder() {
        return transaction.execute(_ -> role().holder());
    }

    PrimaryRole role() {
        return PrimaryRole.among(sessions.running(), processes::isRunning);
    }
}
