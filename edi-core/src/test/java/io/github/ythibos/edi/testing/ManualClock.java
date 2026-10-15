package io.github.ythibos.edi.testing;

import java.time.Duration;
import java.time.Instant;

import io.github.ythibos.edi.application.port.Clock;

public final class ManualClock implements Clock {

    private Instant now;

    public ManualClock(Instant start) {
        this.now = start;
    }

    @Override
    public Instant now() {
        return now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
