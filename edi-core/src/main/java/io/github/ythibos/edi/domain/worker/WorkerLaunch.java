package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record WorkerLaunch(LaunchKind kind, Instant at, BootId boot, LoginName login) {

    public WorkerLaunch {
        Objects.requireNonNull(kind, "A launch is a dispatch, a resume or a fresh start.");
        Objects.requireNonNull(at, "A launch records when it happened.");
        Objects.requireNonNull(login, "A launch records the Claude login it runs on.");
    }

    public boolean happenedBefore(BootId currentBoot) {
        return boot != null && !boot.equals(currentBoot);
    }
}
