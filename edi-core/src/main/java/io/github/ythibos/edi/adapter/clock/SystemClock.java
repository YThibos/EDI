package io.github.ythibos.edi.adapter.clock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Clock;

@Component
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
