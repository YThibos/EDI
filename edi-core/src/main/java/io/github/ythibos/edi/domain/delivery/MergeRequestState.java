package io.github.ythibos.edi.domain.delivery;

import java.util.Arrays;
import java.util.Locale;

public enum MergeRequestState {
    OPEN,
    MERGED,
    CLOSED;

    public static MergeRequestState named(String word) {
        return Arrays.stream(values())
                .filter(state -> state.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A merge request is open, merged or closed, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
