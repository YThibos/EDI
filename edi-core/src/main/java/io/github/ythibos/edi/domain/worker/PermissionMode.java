package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum PermissionMode {
    BYPASS,
    AUTO,
    PROMPT;

    public static PermissionMode named(String word) {
        return Arrays.stream(values())
                .filter(mode -> mode.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Workers run their tools in bypass, auto or prompt mode, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
