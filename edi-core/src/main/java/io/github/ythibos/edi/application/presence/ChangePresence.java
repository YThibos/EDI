package io.github.ythibos.edi.application.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.application.dispatch.WorkerPlaces;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;

@Service
public class ChangePresence {

    static final Duration LONGEST_DEMO = Duration.ofHours(3);

    private final PresenceStore presence;
    private final WorkerPlaces places;
    private final MeetingCalendar calendar;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ChangePresence(PresenceStore presence, WorkerPlaces places, MeetingCalendar calendar, Clock clock, TransactionTemplate transaction) {
        this.presence = presence;
        this.places = places;
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
        Mandate mandate = new Mandate(words, places.occupied());
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
        Optional<Presence> left = presence.unbriefedAbsence().or(() -> Optional.of(current).filter(Presence::keepsTheChatQuiet));
        if (!current.isPresent()) {
            presence.save(current.captainTyped(at));
        }
        presence.forgetUnbriefedAbsence();
        return left;
    }

    private Presence changed(UnaryOperator<Presence> change) {
        return transaction.execute(_ -> {
            Presence current = currentAt(clock.now());
            Presence changed = change.apply(current);
            if (changed.returnsFrom(current)) {
                presence.oweABriefFor(current);
            }
            presence.save(changed);
            return changed;
        });
    }

    private Presence currentAt(Instant at) {
        return presence.current().orElseGet(() -> Presence.presentSince(at));
    }
}
