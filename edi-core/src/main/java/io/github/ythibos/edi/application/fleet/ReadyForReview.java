package io.github.ythibos.edi.application.fleet;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.task.Task;

public record ReadyForReview(Task task, Delivery delivery) {
}
