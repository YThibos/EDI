package io.github.ythibos.edi.application.wake;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WakeStore;
import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class WaitForWakes {

    private final WakeStore wakes;
    private final PrimarySessions sessions;
    private final DecisionStore decisions;
    private final TaskStore tasks;
    private final OutageStore outages;
    private final PresenceStore presence;
    private final PersonalConfiguration personal;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WaitForWakes(WakeStore wakes, PrimarySessions sessions, DecisionStore decisions, TaskStore tasks, OutageStore outages,
            PresenceStore presence, PersonalConfiguration personal, Clock clock, TransactionTemplate transaction) {
        this.wakes = wakes;
        this.sessions = sessions;
        this.decisions = decisions;
        this.tasks = tasks;
        this.outages = outages;
        this.presence = presence;
        this.personal = personal;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void requireThePrimary(UUID session) {
        if (session == null || sessions.standingOf(session) != Standing.PRIMARY) {
            throw new Refusal("edi wait serves the primary session, the one the captain typed in last; this session is not it.");
        }
    }

    public Optional<WakeDelivery> deliverTo(UUID session) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            wakes.acknowledgeDeliveredTo(session, now);
            List<QueuedWake> pending = wakes.unacknowledged();
            Presence captain = presence.current().orElseGet(() -> Presence.presentSince(now));
            if (pending.isEmpty() || !theBatchIsDue(pending, captain, now)) {
                return Optional.<WakeDelivery>empty();
            }
            wakes.deliver(pending, session, now);
            return Optional.of(new WakeDelivery(pending, decisions.open().size(), tasks.inState(TaskState.IN_FLIGHT).size(), outages.open().size(),
                    captain));
        });
    }

    public void withdraw(WakeDelivery undelivered) {
        transaction.executeWithoutResult(_ -> wakes.withdraw(undelivered.wakes()));
    }

    private boolean theBatchIsDue(List<QueuedWake> pending, Presence captain, Instant now) {
        if (!captain.keepsTheChatQuiet()) {
            return true;
        }
        Instant gatheredSince = pending.stream().map(QueuedWake::raisedAt).min(Instant::compareTo).orElse(now);
        return !now.isBefore(gatheredSince.plus(personal.presence().quietBatch()));
    }
}
