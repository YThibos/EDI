package io.github.ythibos.edi.domain.worker;

import java.time.Instant;

public sealed interface WorkerHealth {

    default boolean isDead() {
        return this instanceof Dead;
    }

    record Starting(Instant since) implements WorkerHealth {
    }

    record Busy(Instant since) implements WorkerHealth {
    }

    record Idle(Instant since) implements WorkerHealth {
    }

    record Waiting(Instant until) implements WorkerHealth {
    }

    record LimitParked(UsageLimit limit) implements WorkerHealth {
    }

    record Stopped(Instant at) implements WorkerHealth {
    }

    record Finished() implements WorkerHealth {
    }

    record Dead(DeathCause cause) implements WorkerHealth {
    }
}
