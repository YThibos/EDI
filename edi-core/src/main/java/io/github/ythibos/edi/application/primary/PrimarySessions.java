package io.github.ythibos.edi.application.primary;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.domain.primary.PrimaryRole;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryTurn;
import io.github.ythibos.edi.domain.primary.RoleReturn;
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

    public Optional<RoleReturn> reclaimIfItsTakerLeft(UUID session, Instant now) {
        return transaction.execute(_ -> role().holder().filter(holder -> holder.id().equals(session)).flatMap(holder -> reclaimedBy(holder, now)));
    }

    public void withdrawTheReclaim(UUID session, RoleReturn undelivered) {
        transaction.executeWithoutResult(_ -> sessions.get(session).ifPresent(holder -> sessions.save(holder.claimed(undelivered.heldSince()))));
    }

    public Optional<Instant> previousReportOf(UUID session) {
        return transaction.execute(_ -> sessions.get(session).map(PrimarySession::turn).map(PrimaryTurn::previousReportAt));
    }

    PrimaryRole role() {
        return PrimaryRole.among(sessions.running(), processes::isRunning);
    }

    private Optional<RoleReturn> reclaimedBy(PrimarySession holder, Instant now) {
        Optional<RoleReturn> back = RoleReturn.toTheHolder(holder, sessions.all());
        back.ifPresent(_ -> sessions.save(holder.claimed(now)));
        return back;
    }
}
