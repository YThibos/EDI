package io.github.ythibos.edi.application.fleet;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

public record FleetSnapshot(WaitingOnTheCaptain waiting, DealtWith dealtWith, List<OngoingTask> ongoing, List<ChangingRequest> changing,
        List<ParkedTask> parked, List<Task> queued, List<LandedTask> landed) {

    public FleetSnapshot {
        ongoing = List.copyOf(ongoing);
        changing = List.copyOf(changing);
        parked = List.copyOf(parked);
        queued = List.copyOf(queued);
        landed = List.copyOf(landed);
    }

    public boolean isQuiet() {
        return waiting.isEmpty() && ongoing.isEmpty();
    }

    public Optional<ChangingRequest> comingBackOf(TaskId task) {
        return ChangingRequest.comingBackOf(task, changing);
    }
}
