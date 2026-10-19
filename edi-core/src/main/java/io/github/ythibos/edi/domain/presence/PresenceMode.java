package io.github.ythibos.edi.domain.presence;

import java.util.Arrays;
import java.util.Locale;

public enum PresenceMode {
    PRESENT,
    QUIET,
    AWAY;

    public static PresenceMode named(String word) {
        return Arrays.stream(values())
                .filter(mode -> mode.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Presence is present, quiet or away, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
