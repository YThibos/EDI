package io.github.ythibos.edi.domain.worker;

import java.time.Instant;

public record UsageLimit(String name, Instant resetsAt) {

    public UsageLimit {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A usage limit is named, such as the session limit.");
        }
        name = name.strip();
    }

    public boolean hasResetBy(Instant now) {
        return resetsAt != null && !now.isBefore(resetsAt);
    }

    public boolean holdsAt(Instant now) {
        return resetsAt == null || now.isBefore(resetsAt);
    }
}
