package io.github.ythibos.edi.domain.spark;

import java.util.Locale;

public enum SparkOutcome {
    BACKLOGGED,
    FOLDED,
    DROPPED;

    public static SparkOutcome named(String word) {
        return valueOf(word.toUpperCase(Locale.ROOT));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean namesABacklogItem() {
        return this != DROPPED;
    }
}
