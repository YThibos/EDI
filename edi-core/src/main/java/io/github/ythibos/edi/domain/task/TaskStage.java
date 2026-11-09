package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
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

    public static TaskStage named(String word) {
        return Arrays.stream(values())
                .filter(stage -> stage.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A task stage is one of " + Arrays.toString(values()) + ", not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isOngoing() {
        return this == WORKING || this == CHANGING;
    }

    public boolean isHandedOver() {
        return this == DELIVERED || this == READY;
    }

    public boolean isMergeable() {
        return isOngoing() || isHandedOver();
    }

    public boolean hasLanded() {
        return this == LANDED || this == RETIRED;
    }

    public boolean isClosed() {
        return this == RETIRED || this == DROPPED;
    }

    public TaskState state() {
        return switch (this) {
            case QUEUED -> TaskState.QUEUED;
            case WORKING, DELIVERED, CHANGING, READY, MERGED, LANDED -> TaskState.IN_FLIGHT;
            case RETIRED -> TaskState.DONE;
            case DROPPED -> TaskState.DROPPED;
        };
    }
}
