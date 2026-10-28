package io.github.ythibos.edi.web.hook;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

enum HookEvent {
    SESSION_START,
    USER_PROMPT_SUBMIT,
    PRE_TOOL_USE,
    STOP,
    STOP_FAILURE,
    NOTIFICATION,
    SESSION_END;

    static HookEvent named(String word) {
        return Arrays.stream(values())
                .filter(event -> event.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The hooks answered are session-start, user-prompt-submit, pre-tool-use, stop,"
                        + " stop-failure, notification and session-end, not " + word + "."));
    }

    String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    String claudeCodeName() {
        return Arrays.stream(name().split("_"))
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
    }
}
