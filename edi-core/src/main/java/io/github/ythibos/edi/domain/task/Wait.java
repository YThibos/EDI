package io.github.ythibos.edi.domain.task;

public record Wait(TaskId task, AwaitedStage awaited, TaskStage stage) {

    public static Wait afterLanded(TaskId blocker, TaskStage stage) {
        return new Wait(blocker, AwaitedStage.LANDED, stage);
    }

    public boolean isMet() {
        return stage.isClosed() || awaited.isReachedBy(stage);
    }

    public String words() {
        return task + " " + awaited.goal();
    }
}
