package io.github.ythibos.edi.application.presence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.adapter.store.PrimarySessionStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.PrimarySession;

@Component
public class FollowThePresence {

    private final PresenceStore presence;
    private final PrimarySessionStore sessions;
    private final MeetingCalendar calendar;
    private final PersonalConfiguration personal;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FollowThePresence(PresenceStore presence, PrimarySessionStore sessions, MeetingCalendar calendar, PersonalConfiguration personal,
            Clock clock, TransactionTemplate transaction) {
        this.presence = presence;
        this.sessions = sessions;
        this.calendar = calendar;
        this.personal = personal;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void follow() {
        Instant now = clock.now();
        List<CalendarEntry> meetings = calendar.meetingInProgressAt(now).stream().toList();
        transaction.executeWithoutResult(_ -> {
            Optional<Presence> stored = presence.current();
            Presence current = stored.orElseGet(() -> Presence.presentSince(lastTypedPrompt().orElse(now)));
            Presence followed = followed(current, meetings, now);
            if (stored.isEmpty() || !followed.equals(current)) {
                presence.save(followed);
            }
        });
    }

    private Presence followed(Presence current, List<CalendarEntry> meetings, Instant now) {
        Presence demoEnded = current.demoEndedBy(now);
        Instant lastSign = lastSignOfTheCaptain(demoEnded);
        Presence afterAMeeting = meetings.isEmpty() ? demoEnded : demoEnded.quietForAMeeting(meetings.getFirst().start(), lastSign, now);
        return afterAMeeting.quietAfterSilence(lastSign, personal.presence().autoQuietAfter(), now);
    }

    private Instant lastSignOfTheCaptain(Presence current) {
        return lastTypedPrompt().filter(typed -> typed.isAfter(current.since())).orElse(current.since());
    }

    private Optional<Instant> lastTypedPrompt() {
        return sessions.all().stream().map(PrimarySession::typedAt).filter(Objects::nonNull).max(Instant::compareTo);
    }
}
