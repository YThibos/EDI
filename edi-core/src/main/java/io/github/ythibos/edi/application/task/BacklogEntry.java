package io.github.ythibos.edi.application.task;

import java.util.List;

import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.Task;

public record BacklogEntry(Task task, List<Blocker> blockers) {

    public BacklogEntry {
        blockers = List.copyOf(blockers);
    }

    public boolean isReady() {
        return !task.isHeld() && blockers.stream().allMatch(Blocker::isResolved);
    }
}
