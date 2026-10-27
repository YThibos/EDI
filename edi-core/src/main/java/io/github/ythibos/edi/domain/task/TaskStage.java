package io.github.ythibos.edi.domain.task;

import java.util.Locale;

public enum TaskStage {
    QUEUED,
    WORKING,
    DELIVERED,
    CHANGING,
    READY,
    MERGED,
    LANDED,
    RETIRED,
    DROPPED;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isOngoing() {
        return this == WORKING || this == CHANGING;
    }
}
