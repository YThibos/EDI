package io.github.ythibos.edi.domain.console;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record SignInLink(String digest, Instant issuedAt, Instant expiresAt, Instant usedAt) {

    public static final Duration LIFETIME = Duration.ofMinutes(2);

    public SignInLink {
        Objects.requireNonNull(digest, "A sign-in link is known by the digest of its token.");
        Objects.requireNonNull(issuedAt, "A sign-in link records when it was issued.");
        Objects.requireNonNull(expiresAt, "A sign-in link expires.");
    }

    public static SignInLink issued(String digest, Instant at) {
        return new SignInLink(digest, at, at.plus(LIFETIME), null);
    }

    public boolean signsInAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public SignInLink used(Instant at) {
        return new SignInLink(digest, issuedAt, expiresAt, at);
    }
}
