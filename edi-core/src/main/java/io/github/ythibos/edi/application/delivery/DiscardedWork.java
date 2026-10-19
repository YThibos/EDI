package io.github.ythibos.edi.application.delivery;

import java.util.List;

import io.github.ythibos.edi.domain.task.TaskId;

public record DiscardedWork(TaskId task, List<String> discarded) {

    public DiscardedWork {
        discarded = List.copyOf(discarded);
    }
}
