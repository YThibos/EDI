package io.github.ythibos.edi.domain.presence;

import java.time.Instant;
import java.util.Objects;

public record UnreadableCalendar(Instant since, String reason) {

    public UnreadableCalendar {
        Objects.requireNonNull(since, "An unreadable calendar is unreadable since some moment.");
        reason = Objects.requireNonNullElse(reason, "").strip();
    }

    UnreadableCalendar stillUnreadable(String latestReason) {
        return new UnreadableCalendar(since, latestReason);
    }
}
