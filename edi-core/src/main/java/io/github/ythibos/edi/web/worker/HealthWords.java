package io.github.ythibos.edi.web.worker;

import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.web.text.Moments;

public final class HealthWords {

    private HealthWords() {
    }

    public static String of(WorkerHealth health) {
        return switch (health) {
            case WorkerHealth.Starting _ -> "starting";
            case WorkerHealth.Busy busy -> "busy since " + Moments.minute(busy.since());
            case WorkerHealth.Idle idle -> "idle since " + Moments.minute(idle.since());
            case WorkerHealth.Waiting waiting -> "waiting until " + Moments.minute(waiting.until());
            case WorkerHealth.LimitParked parked -> "parked on " + limit(parked.limit());
            case WorkerHealth.Stopped stopped -> "stopped by EDI at " + Moments.minute(stopped.at());
            case WorkerHealth.Finished _ -> "finished";
            case WorkerHealth.Dead dead -> "dead: " + dead.cause().sentence();
        };
    }

    public static String limit(UsageLimit limit) {
        return limit.resetsAt() == null ? "the " + limit.name() : "the " + limit.name() + " until " + Moments.minute(limit.resetsAt());
    }
}
