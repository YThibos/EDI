package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record WorkerLaunch(LaunchKind kind, Instant at, BootId boot) {

    public WorkerLaunch {
        Objects.requireNonNull(kind, "A launch is a dispatch, a resume or a fresh start.");
        Objects.requireNonNull(at, "A launch records when it happened.");
    }

    public boolean happenedBefore(BootId currentBoot) {
        return boot != null && !boot.equals(currentBoot);
    }
}
