package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Objects;

public record PrimaryTurn(Instant startedAt, long eventMark, Instant reportedAt, int stopsBlocked) {

    public PrimaryTurn {
        Objects.requireNonNull(startedAt, "A turn records when its prompt arrived.");
        if (eventMark < 0 || stopsBlocked < 0) {
            throw new IllegalArgumentException("A turn's event mark and its count of blocked stops are never negative.");
        }
    }

    public static PrimaryTurn startedAt(Instant at, long eventMark) {
        return new PrimaryTurn(at, eventMark, null, 0);
    }

    public PrimaryTurn reported(Instant at) {
        return new PrimaryTurn(startedAt, eventMark, at, stopsBlocked);
    }

    public PrimaryTurn stopBlocked() {
        return new PrimaryTurn(startedAt, eventMark, reportedAt, stopsBlocked + 1);
    }

    public boolean renderedTheReport() {
        return reportedAt != null && !reportedAt.isBefore(startedAt);
    }
}
