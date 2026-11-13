package io.github.ythibos.edi.domain.plan;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;

public record SliceStanding(Slice slice, Task task, List<Wait> waits) {

    public SliceStanding {
        waits = List.copyOf(waits);
    }

    public boolean isEligible() {
        return task.stage() == TaskStage.QUEUED && !task.isHeld() && waits.stream().allMatch(Wait::isMet);
    }
}
