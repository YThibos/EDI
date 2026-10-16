package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record WorkerWindow(String socket, long serverPid, Instant serverStartedAt, String windowId, long panePid) {

    public WorkerWindow {
        Objects.requireNonNull(socket, "A worker window lives on a named tmux socket.");
        Objects.requireNonNull(serverStartedAt, "A worker window records when its tmux server started.");
        Objects.requireNonNull(windowId, "A worker window has a tmux window id.");
    }
}
