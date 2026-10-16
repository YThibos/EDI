package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum DeathCause {
    MACHINE_RESTARTED("the machine restarted"),
    SERVER_GONE("its tmux server is gone"),
    SERVER_CHANGED("its tmux server changed"),
    WINDOW_CLOSED("its window closed");

    private final String sentence;

    DeathCause(String sentence) {
        this.sentence = sentence;
    }

    public static DeathCause named(String word) {
        return Arrays.stream(values())
                .filter(cause -> cause.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A worker dies because the machine restarted, its server went or changed, or its window closed, not " + word + "."));
    }

    public static DeathCause of(WindowCheck window) {
        return switch (window) {
            case WINDOW_CLOSED -> WINDOW_CLOSED;
            case SERVER_CHANGED -> SERVER_CHANGED;
            case SERVER_GONE -> SERVER_GONE;
            case OPEN -> throw new IllegalArgumentException("An open window is no cause of death.");
        };
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String sentence() {
        return sentence;
    }
}
