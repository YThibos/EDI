package io.github.ythibos.edi.domain.task;

import java.util.List;

public record Wait(TaskId task, AwaitedStage awaited, TaskStage stage) {

    public static Wait afterLanded(TaskId blocker, TaskStage stage) {
        return new Wait(blocker, AwaitedStage.LANDED, stage);
    }

    public static List<Wait> unmet(List<Wait> waits) {
        return waits.stream().filter(wait -> !wait.isMet()).toList();
    }

    public boolean isMet() {
        return stage.isClosed() || awaited.isReachedBy(stage);
    }

    public String words() {
        return task + " " + awaited.goal();
    }
}
