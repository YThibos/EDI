package io.github.ythibos.edi.application.fleet;

import java.time.Duration;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

public record WorkerQuestion(Task task, Worker worker, Duration waited) {

    public String question() {
        return worker.lastStatus().report().text();
    }
}
