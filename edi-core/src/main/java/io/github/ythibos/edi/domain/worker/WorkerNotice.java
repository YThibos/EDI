package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.Objects;

public record WorkerNotice(NoticeKind kind, Instant since) {

    public WorkerNotice {
        Objects.requireNonNull(kind, "A notice names what was noticed.");
        Objects.requireNonNull(since, "A notice names since when its condition holds.");
    }
}
