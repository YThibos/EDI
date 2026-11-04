package io.github.ythibos.edi.domain.delivery;

import java.util.Arrays;
import java.util.Locale;

public enum ForgeKind {
    GITLAB("/-/merge_requests/", "/-/tree/"),
    GITHUB("/pull/", "/tree/");

    private final String mergeRequestPath;
    private final String treePath;

    ForgeKind(String mergeRequestPath, String treePath) {
        this.mergeRequestPath = mergeRequestPath;
        this.treePath = treePath;
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

    String treePath() {
        return treePath;
    }
}
