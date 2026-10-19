package io.github.ythibos.edi.domain.presence;

import java.util.Arrays;
import java.util.Locale;

public enum PresenceCause {
    CAPTAIN,
    SILENCE,
    MEETING;

    public static PresenceCause named(String word) {
        return Arrays.stream(values())
                .filter(cause -> cause.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A presence is set by the captain, by silence or by a meeting, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
