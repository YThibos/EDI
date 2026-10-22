package io.github.ythibos.edi.domain.primary;

import java.time.Duration;
import java.time.Instant;

public record SessionProcess(long pid, Instant startedAt) {

    private static final Duration BOOT_TIME_DRIFT = Duration.ofSeconds(2);

    public SessionProcess {
        if (pid < 1) {
            throw new IllegalArgumentException("A Claude Code session runs as a process with a positive id, not " + pid + ".");
        }
    }

    public boolean isTheOneStartedAt(Instant observedStart) {
        return startedAt == null || withinTheBootTimeDrift(Duration.between(startedAt, observedStart));
    }

    private static boolean withinTheBootTimeDrift(Duration shift) {
        return shift.abs().compareTo(BOOT_TIME_DRIFT) <= 0;
    }
}
