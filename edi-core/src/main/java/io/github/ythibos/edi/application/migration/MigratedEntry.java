package io.github.ythibos.edi.application.migration;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;

record MigratedEntry(Task task, Decision decision) {
}
