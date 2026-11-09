package io.github.ythibos.edi.domain.rule;

import java.util.Locale;

public enum RuleArea {
    GUARD,
    HOOK,
    ACCESS,
    REQUEST,
    AUTHORITY,
    TASK,
    DECISION,
    DISPATCH,
    WORKER,
    INBOX,
    STATUS,
    DELIVERY,
    PUBLISH,
    CLEANUP,
    MECHANICAL,
    BOARD,
    TRACKER,
    WAKE,
    MEMORY,
    CORE,
    RECOVERY,
    MIGRATION,
    COMMIT,
    COMMAND;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
