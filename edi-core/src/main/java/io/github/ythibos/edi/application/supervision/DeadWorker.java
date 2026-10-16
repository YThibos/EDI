package io.github.ythibos.edi.application.supervision;

import java.util.List;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.worker.WorkerList;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

record DeadWorker(Worker worker, DeathCause cause) {

    static List<DeadWorker> among(WorkerList workers) {
        return workers.entries().stream()
                .flatMap(entry -> entry.health() instanceof WorkerHealth.Dead dead ? Stream.of(new DeadWorker(entry.worker(), dead.cause())) : Stream.empty())
                .toList();
    }
}
