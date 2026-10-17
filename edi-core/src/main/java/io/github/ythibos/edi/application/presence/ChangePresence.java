package io.github.ythibos.edi.application.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;

@Service
public class ChangePresence {

    static final Duration LONGEST_DEMO = Duration.ofHours(3);

    private final PresenceStore presence;
    private final WorkerListQuery workers;
    private final MeetingCalendar calendar;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ChangePresence(PresenceStore presence, WorkerListQuery workers, MeetingCalendar calendar, Clock clock, TransactionTemplate transaction) {
        this.presence = presence;
        this.workers = workers;
        this.calendar = calendar;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Presence present() {
        return changed(current -> current.present(clock.now()));
    }

    public Presence quiet() {
        return changed(current -> current.quiet(clock.now()));
    }

    public Presence away(String words) {
        Mandate mandate = new Mandate(words, workers.serving().entries().size());
        return changed(current -> current.away(mandate, clock.now()));
    }

    public Presence demo() {
        Instant now = clock.now();
        Instant until = calendar.meetingInProgressAt(now).map(CalendarEntry::end).filter(end -> end.isBefore(now.plus(LONGEST_DEMO)))
                .orElse(now.plus(LONGEST_DEMO));
        return changed(current -> current.demo(now, until));
    }

    public Presence demoOff() {
        return changed(Presence::demoOff);
    }

    public Optional<Presence> captainTypedAt(Instant at) {
        Presence current = currentAt(at);
        if (current.isPresent()) {
            return Optional.empty();
        }
        presence.save(current.captainTyped(at));
        return Optional.of(current);
    }

    private Presence changed(UnaryOperator<Presence> change) {
        return transaction.execute(_ -> {
            Presence changed = change.apply(currentAt(clock.now()));
            presence.save(changed);
            return changed;
        });
    }

    private Presence currentAt(Instant at) {
        return presence.current().orElseGet(() -> Presence.presentSince(at));
    }
}
