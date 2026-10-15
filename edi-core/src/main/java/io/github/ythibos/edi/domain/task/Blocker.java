package io.github.ythibos.edi.domain.task;

public record Blocker(TaskId id, TaskState state) {

    public boolean isResolved() {
        return state == TaskState.DONE;
    }
}
