package io.github.ythibos.edi.domain.personality;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Scale {

    HUMOUR("humour"),
    FORMALITY("formality"),
    VERBOSITY("verbosity"),
    WARMTH("warmth"),
    DIRECTNESS("directness"),
    INITIATIVE("initiative"),
    DEPTH("depth"),
    ENTHUSIASM("enthusiasm"),
    FLAVOUR("flavour");

    private final String word;

    Scale(String word) {
        this.word = word;
    }

    public String word() {
        return word;
    }

    public static Scale named(String word) {
        return Arrays.stream(values())
                .filter(scale -> scale.word.equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(word + " is not a personality scale; the scales are " + listed() + "."));
    }

    private static String listed() {
        return Arrays.stream(values()).map(Scale::word).collect(Collectors.joining(", "));
    }
}
