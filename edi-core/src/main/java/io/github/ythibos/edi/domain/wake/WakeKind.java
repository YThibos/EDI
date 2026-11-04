package io.github.ythibos.edi.domain.wake;

import java.util.Arrays;
import java.util.Locale;

public enum WakeKind {
    NEEDS_DECISION,
    BLOCKED,
    FAILED,
    DONE,
    LAUNCH_FAILED,
    DEFERRED_LAUNCH_FAILED,
    USAGE_LIMIT,
    LIMIT_RESUME_HELD_BACK,
    CONTEXT_FULL,
    STOP_MISSED,
    STOP_MISSED_NOTICE_WAITING,
    WORKER_DIED,
    LOST_IN_OUTAGE,
    WORKER_STALE,
    BUSY_TOO_LONG,
    HEAVY_RUNS_AT_ONCE,
    WAIT_ENDED,
    QUESTION_UNANSWERED,
    HOLD_DUE,
    DRAFT_MERGED,
    DRAFT_CLOSED,
    DELIVERY_FAILED,
    CLEANUP_REFUSED,
    DECISION_RAISED,
    TASK_KEYED,
    TICKET_REFUSED,
    DECISION_ANSWERED,
    RECHECK_ASKED,
    BOARD_ANSWERED,
    BOARD_ANSWER_REFUSED;

    public static WakeKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No wake is called " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
