package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record LimitStop(Instant endedAt, UsageLimit limit) {

    public LimitStop {
        Objects.requireNonNull(endedAt, "A turn that ended on a usage limit records when it ended.");
        Objects.requireNonNull(limit, "A turn that ended on a usage limit names the limit.");
    }
}
