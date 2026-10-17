package io.github.ythibos.edi.web.presence;

import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.web.text.Moments;

public final class PresenceWords {

    private PresenceWords() {
    }

    public static String of(Presence presence) {
        return switch (presence.mode()) {
            case PRESENT -> presence.demoUntil() == null ? "present" : "present for a demo until " + Moments.minute(presence.demoUntil());
            case QUIET -> "quiet since " + Moments.minute(presence.since()) + ", " + causeOf(presence);
            case AWAY -> "away since " + Moments.minute(presence.since()) + ", on the words \"" + presence.mandate().words() + "\", with at most "
                    + presence.mandate().workerCap() + " workers at a time";
        };
    }

    private static String causeOf(Presence presence) {
        return switch (presence.cause()) {
            case CAPTAIN -> "on request";
            case SILENCE -> "after a stretch without a typed message";
            case MEETING -> "because a meeting began";
        };
    }
}
