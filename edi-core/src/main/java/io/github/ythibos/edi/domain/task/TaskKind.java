package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Locale;

public enum TaskKind {
    SHIP,
    SCOUT,
    CAPTAIN;

    public static TaskKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A task kind is ship, scout or captain, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
