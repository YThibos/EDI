package io.github.ythibos.edi.domain.dispatch;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum EffortLevel {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    private static final EffortLevel CEILING = HIGH;

    public static EffortLevel withinTheCeiling(String word) {
        EffortLevel level = named(word);
        if (level.isAboveTheCeiling()) {
            throw new IllegalArgumentException(beyondTheCeiling(word) + ".");
        }
        return level;
    }

    public static EffortLevel named(String word) {
        return Arrays.stream(values())
                .filter(level -> level.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(beyondTheCeiling(word)
                        + "; only the captain's word for one task lifts that task to " + wordsAboveTheCeiling() + "."));
    }

    public boolean isAboveTheCeiling() {
        return compareTo(CEILING) > 0;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static String beyondTheCeiling(String word) {
        return "Effort is low, medium or high, and high is EDI's ceiling, so not " + word;
    }

    private static String wordsAboveTheCeiling() {
        return Arrays.stream(values()).filter(EffortLevel::isAboveTheCeiling).map(EffortLevel::word).collect(Collectors.joining(" or "));
    }
}
