package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;

public record ParkedTask(Task task, Decision decision, List<Task> blockers) {

    public ParkedTask {
        blockers = List.copyOf(blockers);
    }

    public boolean isBlocked() {
        return !blockers.isEmpty();
    }
}
