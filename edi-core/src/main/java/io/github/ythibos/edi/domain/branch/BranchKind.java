package io.github.ythibos.edi.domain.branch;

import java.util.Arrays;
import java.util.Locale;

public enum BranchKind {
    FEATURE,
    CHORE,
    RESEARCH;

    public static BranchKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A branch kind is feature, chore or research, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
