package io.github.ythibos.edi.domain.tracker;

import java.util.Arrays;
import java.util.Locale;

public enum TrackerKind {
    JIRA,
    EDI,
    GITHUB,
    NONE;

    public static TrackerKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A tracker is jira, edi, github or none, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isExternal() {
        return this == JIRA;
    }

    public boolean keysByIssueNumber() {
        return this == GITHUB;
    }
}
