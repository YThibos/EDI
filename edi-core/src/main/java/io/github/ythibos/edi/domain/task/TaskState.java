package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Locale;

public enum TaskState {
    QUEUED,
    IN_FLIGHT,
    DONE,
    DROPPED;

    public static TaskState named(String word) {
        return Arrays.stream(values())
                .filter(state -> state.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A task state is queued, in-flight, done or dropped, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean isClosed() {
        return this == DONE || this == DROPPED;
    }
}
