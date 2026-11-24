package io.github.ythibos.edi.application.fleet;

import java.util.Optional;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

public record OngoingTask(Task task, Worker worker, PipelineRun pipelineRun, Delivery delivery) {

    public Optional<String> mergedElsewhereOn() {
        return Optional.ofNullable(delivery).filter(Delivery::isMergedElsewhere).map(Delivery::stackedOn);
    }
}
