package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record ErrorStop(Instant endedAt, String error) {

    public ErrorStop {
        Objects.requireNonNull(endedAt, "A turn that ended on an API error records when it ended.");
        Objects.requireNonNull(error, "A turn that ended on an API error names the error, or calls it unknown.");
    }
}
