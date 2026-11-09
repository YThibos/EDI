package io.github.ythibos.edi.domain.rule;

import java.util.Arrays;
import java.util.Optional;

public enum RuleOutcome {
    REFUSED("refused"),
    WARNED("warned"),
    ALLOWED_AFTER_A_CHECK("allowed");

    private final String word;

    RuleOutcome(String word) {
        this.word = word;
    }

    public static Optional<RuleOutcome> named(String word) {
        return Arrays.stream(values()).filter(outcome -> outcome.word.equals(word)).findFirst();
    }

    public String word() {
        return word;
    }
}
