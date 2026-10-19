package io.github.ythibos.edi.application.task;

import io.github.ythibos.edi.domain.task.TaskId;

public record PromotedScout(TaskId scout, AddedTask ship) {
}
