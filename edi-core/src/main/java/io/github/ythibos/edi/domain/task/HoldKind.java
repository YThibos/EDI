package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Locale;

public enum HoldKind {
    DECISION,
    DEFERRAL,
    APPROVAL;

    public static HoldKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A hold is a decision, deferral or approval, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
