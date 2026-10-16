package io.github.ythibos.edi.domain.status;

import java.util.Arrays;
import java.util.Locale;

public enum StatusState {
    WORKING,
    NEEDS_DECISION,
    BLOCKED,
    PAUSED,
    DONE,
    FAILED,
    RESOLVED;

    public static StatusState named(String word) {
        return Arrays.stream(values())
                .filter(state -> state.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "A status is working, needs-decision, blocked, paused, done, failed or resolved, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
