package io.github.ythibos.edi.domain.plan;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;

public record SliceStanding(Slice slice, Task task, List<Wait> waits) {

    public SliceStanding {
        waits = List.copyOf(waits);
    }

    public boolean isEligible() {
        return task.stage() == TaskStage.QUEUED && !task.isHeld() && waits.stream().allMatch(Wait::isMet);
    }

    public List<Wait> unmetWaitsOutside(Plan plan) {
        Optional<TaskId> awaitedInThePlan = plan.taskAwaitedBy(slice);
        return waits.stream().filter(wait -> !wait.isMet() && !isOn(awaitedInThePlan, wait)).toList();
    }

    private static boolean isOn(Optional<TaskId> task, Wait wait) {
        return task.filter(wait.task()::equals).isPresent();
    }
}
