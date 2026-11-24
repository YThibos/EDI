package io.github.ythibos.edi.application.fleet;

import static java.util.function.Predicate.not;

import java.util.Optional;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

public record OngoingTask(Task task, Worker worker, PipelineRun pipelineRun, Delivery delivery) {

    public Optional<Delivery> stackedRequest() {
        return Optional.ofNullable(delivery).filter(Delivery::isStacked).filter(not(Delivery::isClosed));
    }
}
