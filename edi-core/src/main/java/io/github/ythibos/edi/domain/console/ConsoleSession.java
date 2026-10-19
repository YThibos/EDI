package io.github.ythibos.edi.domain.console;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ConsoleSession(String digest, Instant openedAt, Instant expiresAt) {

    public static final Duration LIFETIME = Duration.ofHours(12);

    public ConsoleSession {
        Objects.requireNonNull(digest, "A console session is known by the digest of its cookie.");
        Objects.requireNonNull(openedAt, "A console session records when it opened.");
        Objects.requireNonNull(expiresAt, "A console session expires.");
    }

    public static ConsoleSession opened(String digest, Instant at) {
        return new ConsoleSession(digest, at, at.plus(LIFETIME));
    }

    public boolean isOpenAt(Instant now) {
        return now.isBefore(expiresAt);
    }
}
