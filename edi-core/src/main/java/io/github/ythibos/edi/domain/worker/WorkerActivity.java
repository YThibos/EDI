package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum WorkerActivity {
    STARTING,
    BUSY,
    IDLE;

    public static WorkerActivity named(String word) {
        return Arrays.stream(values())
                .filter(activity -> activity.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A worker is starting, busy or idle, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
