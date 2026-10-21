package io.github.ythibos.edi.application.primary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.HomeSetup;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.application.fleet.FleetSnapshotQuery;
import io.github.ythibos.edi.application.memory.MemoryQuery;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.presence.PresenceQuery;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.WeeklyUpkeep;

@Service
public class SessionStartDigestQuery {

    private final PrimarySessions roles;
    private final PrimarySessionStore sessions;
    private final FleetSnapshotQuery fleet;
    private final WorkerListQuery workers;
    private final OutageStore outages;
    private final WakeStore wakes;
    private final PresenceQuery presence;
    private final MemoryQuery memory;
    private final HomeSetup home;
    private final PersonalConfiguration personal;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SessionStartDigestQuery(PrimarySessions roles, PrimarySessionStore sessions, FleetSnapshotQuery fleet, WorkerListQuery workers,
            OutageStore outages, WakeStore wakes, PresenceQuery presence, MemoryQuery memory, HomeSetup home, PersonalConfiguration personal,
            Clock clock, TransactionTemplate transaction) {
        this.roles = roles;
        this.sessions = sessions;
        this.fleet = fleet;
        this.workers = workers;
        this.outages = outages;
        this.wakes = wakes;
        this.presence = presence;
        this.memory = memory;
        this.home = home;
        this.personal = personal;
        this.clock = clock;
        this.transaction = transaction;
    }

    public SessionStartDigest digestFor(UUID session) {
        return new SessionStartDigest(roles.standingOf(session), personal.address(), transaction.execute(_ -> limitStopToTell(session)),
                fleet.snapshot(), workers.serving(), transaction.execute(_ -> outages.open()), transaction.execute(_ -> wakes.unacknowledged().size()),
                presence.current(), memory.check(), WeeklyUpkeep.isSuggestedOn(LocalDate.ofInstant(clock.now(), ZoneId.systemDefault())),
                home.missing(), null);
    }

    private PrimarySession limitStopToTell(UUID session) {
        List<PrimarySession> all = sessions.all();
        Instant started = all.stream().filter(known -> known.id().equals(session)).map(PrimarySession::startedAt).findFirst().orElse(null);
        return all.stream()
                .filter(other -> !other.id().equals(session) && other.limitReachedAt() != null)
                .max(Comparator.comparing(PrimarySession::limitReachedAt))
                .filter(stopped -> started != null && started.isAfter(stopped.limitReachedAt()))
                .filter(stopped -> isTheFirstSessionSince(all, stopped, session, started))
                .orElse(null);
    }

    private static boolean isTheFirstSessionSince(List<PrimarySession> all, PrimarySession stopped, UUID session, Instant started) {
        return all.stream()
                .filter(other -> !other.id().equals(session) && !other.id().equals(stopped.id()))
                .map(PrimarySession::startedAt)
                .filter(Objects::nonNull)
                .noneMatch(otherStart -> otherStart.isAfter(stopped.limitReachedAt()) && otherStart.isBefore(started));
    }
}
