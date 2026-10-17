package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;

public record FleetSnapshot(List<OpenDecision> decisions, List<OngoingTask> ongoing, List<ParkedTask> parked, List<Task> queued,
        List<LandedTask> landed, List<RecentAnswer> answered) {

    public FleetSnapshot {
        decisions = List.copyOf(decisions);
        ongoing = List.copyOf(ongoing);
        parked = List.copyOf(parked);
        queued = List.copyOf(queued);
        landed = List.copyOf(landed);
        answered = List.copyOf(answered);
    }

    public boolean isQuiet() {
        return decisions.isEmpty() && ongoing.isEmpty();
    }
}
