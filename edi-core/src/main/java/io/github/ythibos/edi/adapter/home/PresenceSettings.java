package io.github.ythibos.edi.adapter.home;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record PresenceSettings(Duration autoQuietAfter, Duration quietBatch, List<String> calendarIgnore) {

    public static final Duration AUTO_QUIET_AFTER = Duration.ofMinutes(15);
    public static final Duration QUIET_BATCH = Duration.ofMinutes(30);

    public PresenceSettings {
        Objects.requireNonNull(autoQuietAfter, "presence.autoQuietAfter says after how long without a typed prompt presence turns quiet.");
        Objects.requireNonNull(quietBatch, "presence.quietBatch says how long quiet wakes are gathered.");
        calendarIgnore = List.copyOf(calendarIgnore);
        if (autoQuietAfter.isNegative() || autoQuietAfter.isZero() || quietBatch.isNegative()) {
            throw new IllegalArgumentException("presence.autoQuietAfter is a positive duration and presence.quietBatch is never negative.");
        }
    }
}
