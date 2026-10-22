package io.github.ythibos.edi.testing;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import io.github.ythibos.edi.application.port.Clock;

public final class ManualClock implements Clock {

    public static final ZoneId UTC = ZoneId.of("UTC");

    private final ZoneId zone;
    private Instant now;

    public ManualClock(Instant start) {
        this(start, UTC);
    }

    public ManualClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public ZoneId zone() {
        return zone;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
