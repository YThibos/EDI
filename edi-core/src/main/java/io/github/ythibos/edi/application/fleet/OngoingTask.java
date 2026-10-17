package io.github.ythibos.edi.application.fleet;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;

public record OngoingTask(Task task, Worker worker, PipelineRun pipelineRun, Delivery delivery) {
}
