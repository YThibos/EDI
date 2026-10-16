package io.github.ythibos.edi.domain.project;

import java.util.Arrays;
import java.util.Locale;

public enum AiScaffolding {
    COMMITTED,
    LOCAL_ONLY,
    ASK;

    public static AiScaffolding named(String word) {
        return Arrays.stream(values())
                .filter(posture -> posture.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AI scaffolding is committed, local-only or ask, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
