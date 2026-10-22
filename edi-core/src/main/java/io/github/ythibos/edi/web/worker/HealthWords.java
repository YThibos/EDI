package io.github.ythibos.edi.web.worker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerHealth;
import io.github.ythibos.edi.web.text.Moments;

@Component
public class HealthWords {

    private final Moments moments;

    public HealthWords(Moments moments) {
        this.moments = moments;
    }

    public String of(WorkerHealth health) {
        return switch (health) {
            case WorkerHealth.Starting _ -> "starting";
            case WorkerHealth.Busy busy -> "busy since " + moments.minute(busy.since());
            case WorkerHealth.Idle idle -> "idle since " + moments.minute(idle.since());
            case WorkerHealth.Waiting waiting -> "waiting until " + moments.minute(waiting.until());
            case WorkerHealth.LimitParked parked -> "parked on " + limit(parked.limit());
            case WorkerHealth.Stopped stopped -> "stopped by EDI at " + moments.minute(stopped.at());
            case WorkerHealth.LaunchFailed failed -> "launch failed at " + moments.minute(failed.at());
            case WorkerHealth.Finished _ -> "finished";
            case WorkerHealth.Dead dead -> "dead: " + dead.cause().sentence();
        };
    }

    public String limit(UsageLimit limit) {
        return limit.resetsAt() == null ? "the " + limit.name() : "the " + limit.name() + " until " + moments.minute(limit.resetsAt());
    }
}
