package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Optional;

public record PrimaryWait(Sighting sighting, Instant seenAt, int tasksInFlight) {

    public enum Sighting {
        LIVE,
        MISSING,
        UNCHECKED_IN_A_TURN,
        NO_PRIMARY
    }

    public static PrimaryWait of(Optional<PrimarySession> primary, int tasksInFlight) {
        return primary.map(session -> seenAtTheTurnEndOf(session, tasksInFlight))
                .orElseGet(() -> new PrimaryWait(Sighting.NO_PRIMARY, null, tasksInFlight));
    }

    public boolean needsAWait() {
        return tasksInFlight > 0 && isNotLive();
    }

    private static PrimaryWait seenAtTheTurnEndOf(PrimarySession session, int tasksInFlight) {
        PrimaryTurn turn = session.turn();
        if (turn == null) {
            return new PrimaryWait(Sighting.UNCHECKED_IN_A_TURN, session.startedAt(), tasksInFlight);
        }
        if (!turn.hasEnded()) {
            return new PrimaryWait(Sighting.UNCHECKED_IN_A_TURN, turn.startedAt(), tasksInFlight);
        }
        return new PrimaryWait(turn.endedWithAWait() ? Sighting.LIVE : Sighting.MISSING, turn.endedAt(), tasksInFlight);
    }

    private boolean isNotLive() {
        return sighting == Sighting.MISSING || sighting == Sighting.NO_PRIMARY;
    }
}
