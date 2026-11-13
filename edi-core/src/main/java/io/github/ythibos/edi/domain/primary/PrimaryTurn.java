package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Objects;

public record PrimaryTurn(Instant startedAt, long eventMark, Instant reportedAt, int stopsBlocked, Instant previousReportAt) {

    public PrimaryTurn {
        Objects.requireNonNull(startedAt, "A turn records when its prompt arrived.");
        if (eventMark < 0 || stopsBlocked < 0) {
            throw new IllegalArgumentException("A turn's event mark and its count of blocked stops are never negative.");
        }
    }

    public static PrimaryTurn startedAt(Instant at, long eventMark) {
        return new PrimaryTurn(at, eventMark, null, 0, null);
    }

    public PrimaryTurn followedAt(Instant at, long nextEventMark) {
        return new PrimaryTurn(at, nextEventMark, null, 0, latestReportAt());
    }

    public PrimaryTurn reported(Instant at) {
        return new PrimaryTurn(startedAt, eventMark, at, stopsBlocked, previousReportAt);
    }

    public PrimaryTurn stopBlocked() {
        return new PrimaryTurn(startedAt, eventMark, reportedAt, stopsBlocked + 1, previousReportAt);
    }

    public boolean renderedTheReport() {
        return reportedAt != null && !reportedAt.isBefore(startedAt);
    }

    public boolean reportedSince(Instant at) {
        return renderedTheReport() && !reportedAt.isBefore(at);
    }

    private Instant latestReportAt() {
        return reportedAt == null ? previousReportAt : reportedAt;
    }
}
