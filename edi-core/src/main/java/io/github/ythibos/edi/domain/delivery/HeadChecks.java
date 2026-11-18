package io.github.ythibos.edi.domain.delivery;

import java.util.Arrays;
import java.util.Locale;

public enum HeadChecks {
    PASSED,
    FAILED,
    UNDECIDED;

    public static HeadChecks named(String word) {
        return Arrays.stream(values())
                .filter(checks -> checks.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The checks on a head passed, failed or are undecided, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
