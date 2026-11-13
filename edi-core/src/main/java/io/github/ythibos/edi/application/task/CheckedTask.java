package io.github.ythibos.edi.application.task;

import io.github.ythibos.edi.domain.tracker.WorkKeying;

public record CheckedTask(NewTask newTask, WorkKeying keying) {
}
