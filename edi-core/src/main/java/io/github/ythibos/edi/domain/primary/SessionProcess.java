package io.github.ythibos.edi.domain.primary;

import java.time.Instant;

public record SessionProcess(long pid, Instant startedAt) {

    public SessionProcess {
        if (pid < 1) {
            throw new IllegalArgumentException("A Claude Code session runs as a process with a positive id, not " + pid + ".");
        }
    }
}
