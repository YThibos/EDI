package io.github.ythibos.edi.domain.delivery;

import java.util.Arrays;
import java.util.Locale;

public enum ForgeKind {
    GITLAB("/-/merge_requests/"),
    GITHUB("/pull/");

    private final String mergeRequestPath;

    ForgeKind(String mergeRequestPath) {
        this.mergeRequestPath = mergeRequestPath;
    }

    public static ForgeKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A forge is gitlab or github, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    String mergeRequestPath() {
        return mergeRequestPath;
    }
}
