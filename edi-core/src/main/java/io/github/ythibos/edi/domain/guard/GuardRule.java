package io.github.ythibos.edi.domain.guard;

import java.util.Locale;

public enum GuardRule {
    SIGNAL_BY_PATTERN,
    TMUX_WITHOUT_SOCKET,
    EDI_SERVER_CONTROL,
    FORCED_PUSH,
    BASE_BRANCH_PUSH,
    MERGE,
    OUTSIDE_ISOLATED_COPY,
    SECRET,
    UNREADABLE_COMMAND;

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
