package io.github.ythibos.edi.domain.project;

import java.util.Arrays;
import java.util.Locale;

public enum ShipBriefRule {
    DECISION_LOG_ADR,
    NO_EXPLANATORY_COMMENTS;

    public static ShipBriefRule named(String word) {
        return Arrays.stream(values())
                .filter(rule -> rule.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A ship brief rule is decision-log-adr or no-explanatory-comments, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
