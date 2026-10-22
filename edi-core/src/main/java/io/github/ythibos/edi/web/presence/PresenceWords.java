package io.github.ythibos.edi.web.presence;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.web.text.Moments;

@Component
public class PresenceWords {

    private final Moments moments;

    public PresenceWords(Moments moments) {
        this.moments = moments;
    }

    public String of(Presence presence) {
        return switch (presence.mode()) {
            case PRESENT -> presence.demoUntil() == null ? "present" : "present for a demo until " + moments.minute(presence.demoUntil());
            case QUIET -> "quiet since " + moments.minute(presence.since()) + ", " + causeOf(presence);
            case AWAY -> "away since " + moments.minute(presence.since()) + ", on the words \"" + presence.mandate().words() + "\", with at most "
                    + presence.mandate().workerCap() + " workers at a time";
        };
    }

    public String ofTheUnreadableCalendar(UnreadableCalendar unreadable) {
        return "unreadable since " + moments.minute(unreadable.since()) + ", so a meeting added since the last reading does not turn your presence quiet: \""
                + unreadable.reason() + "\"";
    }

    private static String causeOf(Presence presence) {
        return switch (presence.cause()) {
            case CAPTAIN -> "on request";
            case SILENCE -> "after a stretch without a typed message";
            case MEETING -> "because a meeting began";
        };
    }
}
