package io.github.ythibos.edi.application.task;

import java.util.List;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskState;

public record TaskList(List<TaskState> states, List<Task> tasks) {

    public TaskList {
        states = List.copyOf(states);
        tasks = List.copyOf(tasks);
    }
}
