package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum DoneCheck {
    CHECKING,
    ACCEPTED,
    REFUSED;

    public static DoneCheck named(String word) {
        return Arrays.stream(values())
                .filter(check -> check.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A done is being checked, accepted or refused, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
