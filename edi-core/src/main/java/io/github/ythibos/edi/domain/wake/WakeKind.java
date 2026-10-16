package io.github.ythibos.edi.domain.wake;

import java.util.Arrays;
import java.util.Locale;

public enum WakeKind {
    NEEDS_DECISION,
    BLOCKED,
    FAILED,
    DONE,
    DECISION_ANSWERED,
    STEER_NOT_RECEIVED,
    LAUNCH_FAILED,
    DEFERRED_LAUNCH_FAILED,
    MECHANICAL_ACCEPTED,
    MECHANICAL_REFUSED,
    BRANCH_DELIVERED,
    BRANCH_DELIVERY_FAILED,
    USAGE_LIMIT,
    LIMIT_RESUME_HELD_BACK,
    WORKER_DIED,
    LOST_IN_OUTAGE,
    RECOVERY_FAILED,
    WORKER_STALE,
    BUSY_TOO_LONG,
    WAIT_ENDED,
    HOLD_DUE;

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
