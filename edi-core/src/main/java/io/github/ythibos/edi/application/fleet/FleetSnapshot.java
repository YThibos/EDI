package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;

public record FleetSnapshot(WaitingOnTheCaptain waiting, List<OngoingTask> ongoing, List<ParkedTask> parked, List<Task> queued,
        List<LandedTask> landed, List<RecentAnswer> answered) {

    public FleetSnapshot {
        ongoing = List.copyOf(ongoing);
        parked = List.copyOf(parked);
        queued = List.copyOf(queued);
        landed = List.copyOf(landed);
        answered = List.copyOf(answered);
    }

    public boolean isQuiet() {
        return waiting.isEmpty() && ongoing.isEmpty();
    }
}
