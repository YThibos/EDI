package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record LimitPark(UsageLimit limit, Instant parkedAt) {

    public LimitPark {
        Objects.requireNonNull(limit, "A worker is parked on a named usage limit.");
        Objects.requireNonNull(parkedAt, "A parked worker records when it stopped on its limit.");
    }
}
