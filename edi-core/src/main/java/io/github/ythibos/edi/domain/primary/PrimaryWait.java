package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Optional;

import io.github.ythibos.edi.domain.wake.WakeStream;

public record PrimaryWait(Sighting sighting, Instant since, int tasksInFlight) {

    public enum Sighting {
        FOLLOWING,
        WAITING_ONCE,
        MISSING,
        NO_PRIMARY
    }

    public static PrimaryWait of(Optional<PrimarySession> primary, Optional<WakeStream> liveStream, int tasksInFlight) {
        if (primary.isEmpty()) {
            return new PrimaryWait(Sighting.NO_PRIMARY, null, tasksInFlight);
        }
        return liveStream.map(stream -> new PrimaryWait(sightingOf(stream), stream.startedAt(), tasksInFlight))
                .orElseGet(() -> new PrimaryWait(Sighting.MISSING, null, tasksInFlight));
    }

    public boolean needsAWait() {
        return tasksInFlight > 0 && isNotLive();
    }

    private static Sighting sightingOf(WakeStream stream) {
        return stream.follows() ? Sighting.FOLLOWING : Sighting.WAITING_ONCE;
    }

    private boolean isNotLive() {
        return sighting == Sighting.MISSING || sighting == Sighting.NO_PRIMARY;
    }
}
