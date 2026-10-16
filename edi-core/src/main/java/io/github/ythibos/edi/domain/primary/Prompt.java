package io.github.ythibos.edi.domain.primary;

import java.util.Objects;

import io.github.ythibos.edi.domain.OperationalText;

public record Prompt(String text) {

    private static final String BACKGROUND_JOB_NOTICE = "<task-notification>";

    public Prompt {
        text = Objects.requireNonNullElse(text, "");
    }

    public boolean isTypedByTheCaptain() {
        String opening = text.strip();
        return !opening.isEmpty() && !opening.startsWith(BACKGROUND_JOB_NOTICE) && !OperationalText.isMarked(opening);
    }
}
