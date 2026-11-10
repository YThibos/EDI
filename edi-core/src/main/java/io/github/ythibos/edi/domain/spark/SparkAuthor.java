package io.github.ythibos.edi.domain.spark;

import java.util.Locale;

public enum SparkAuthor {
    CAPTAIN,
    PRIMARY;

    public static SparkAuthor named(String word) {
        return valueOf(word.toUpperCase(Locale.ROOT));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
