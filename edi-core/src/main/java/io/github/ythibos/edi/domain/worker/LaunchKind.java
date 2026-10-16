package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum LaunchKind {
    DISPATCH,
    RESUME,
    FRESH;

    public static LaunchKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A worker is launched by a dispatch, a resume or a fresh start, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isARecovery() {
        return this != DISPATCH;
    }
}
