package io.github.ythibos.edi.domain.plan;

import java.util.Optional;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.Wait;

public record SliceStanding(Slice slice, Task task, Optional<Wait> awaits) {

    public boolean isEligible() {
        return task.stage() == TaskStage.QUEUED && !task.isHeld() && awaits.map(Wait::isMet).orElse(true);
    }
}
