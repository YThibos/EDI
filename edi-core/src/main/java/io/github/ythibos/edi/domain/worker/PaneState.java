package io.github.ythibos.edi.domain.worker;

import java.util.Locale;

public enum PaneState {
    IDLE_PROMPT,
    BUSY,
    DIALOG_LIMIT_WAIT,
    DIALOG_SPEND_CAP,
    DIALOG_CONTINUE_AFTER_RESET,
    DIALOG_TRUST,
    DIALOG_PERMISSION,
    DIALOG_EXIT_PICKER,
    UNKNOWN;

    public boolean acceptsALine() {
        return this == IDLE_PROMPT;
    }

    public boolean isADialog() {
        return name().startsWith("DIALOG_");
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
