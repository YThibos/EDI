package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Locale;

public enum TaskState {
    QUEUED,
    IN_FLIGHT,
    DONE;

    public static TaskState named(String word) {
        return Arrays.stream(values())
                .filter(state -> state.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A task state is queued, in-flight or done, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
