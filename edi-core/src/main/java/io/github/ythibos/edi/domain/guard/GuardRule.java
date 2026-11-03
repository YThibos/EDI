package io.github.ythibos.edi.domain.guard;

import java.util.Locale;

public enum GuardRule {
    SIGNAL_BY_PATTERN,
    TMUX_WITHOUT_SOCKET,
    EDI_SERVER_CONTROL,
    TMUX_INPUT,
    FORCED_PUSH,
    BASE_BRANCH_PUSH,
    MERGE,
    SKIPPED_HOOKS,
    OUTSIDE_ISOLATED_COPY,
    CLAUDE_SETTINGS,
    SECRET,
    WORKER_IDENTITY,
    UNLANDED_BRANCH_DELETION,
    DIALOG_ANSWERED_AHEAD,
    HOOK_FROM_THE_SHELL,
    EDI_WAIT_NOT_ALONE,
    WAKE_STREAM_OUTSIDE_MONITOR,
    UNREADABLE_COMMAND;

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
