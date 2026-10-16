package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;

public record FleetSnapshot(List<OpenDecision> decisions, List<Task> ongoing, List<ParkedTask> parked, List<Task> queued) {

    public FleetSnapshot {
        decisions = List.copyOf(decisions);
        ongoing = List.copyOf(ongoing);
        parked = List.copyOf(parked);
        queued = List.copyOf(queued);
    }

    public boolean isQuiet() {
        return decisions.isEmpty() && ongoing.isEmpty();
    }
}
