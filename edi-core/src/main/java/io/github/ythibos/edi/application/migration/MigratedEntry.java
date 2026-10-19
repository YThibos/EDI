package io.github.ythibos.edi.application.migration;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;

public record MigratedEntry(Task task, Decision decision) {
}
