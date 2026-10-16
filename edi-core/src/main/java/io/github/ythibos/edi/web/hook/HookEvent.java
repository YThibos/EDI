package io.github.ythibos.edi.web.hook;

import java.util.Arrays;
import java.util.Locale;

enum HookEvent {
    SESSION_START,
    USER_PROMPT_SUBMIT,
    PRE_TOOL_USE,
    STOP;

    static HookEvent named(String word) {
        return Arrays.stream(values())
                .filter(event -> event.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EDI answers the hooks session-start, user-prompt-submit, pre-tool-use and stop, not " + word + "."));
    }

    String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
