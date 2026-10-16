package io.github.ythibos.edi.domain.project;

import java.util.Arrays;
import java.util.Locale;

public enum DeliveryMode {
    DRAFT_REVIEW,
    LOCAL_BRANCH,
    LOCAL_APPLY;

    public static DeliveryMode named(String word) {
        return Arrays.stream(values())
                .filter(mode -> mode.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A delivery mode is draft-review, local-branch or local-apply, not " + word + "."));
    }

    public static DeliveryMode registryDefault(String word) {
        DeliveryMode mode = named(word);
        if (mode == LOCAL_APPLY) {
            throw new IllegalArgumentException("local-apply is a delivery the captain asks for on one task, never a project's default;"
                    + " give the task --delivery local-apply instead.");
        }
        return mode;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

}
