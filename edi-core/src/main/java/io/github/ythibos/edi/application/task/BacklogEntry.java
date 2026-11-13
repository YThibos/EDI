package io.github.ythibos.edi.application.task;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.Wait;

public record BacklogEntry(Task task, List<Wait> waits) {

    public BacklogEntry {
        waits = List.copyOf(waits);
    }

    public boolean isReady() {
        return !task.isHeld() && waits.stream().allMatch(Wait::isMet);
    }
}
