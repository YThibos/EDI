package io.github.ythibos.edi.domain.dispatch;

import java.util.Arrays;
import java.util.Locale;

public enum EffortLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static EffortLevel named(String word) {
        return Arrays.stream(values())
                .filter(level -> level.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Effort is low, medium or high, and high is EDI's ceiling, so not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
