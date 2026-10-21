package io.github.ythibos.edi.application.task;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class DependantTasks {

    private final TaskStore tasks;

    public DependantTasks(TaskStore tasks) {
        this.tasks = tasks;
    }

    public void stopWaitingFor(Task closed) {
        tasks.waitingFor(closed.id()).forEach(dependant -> tasks.update(dependant.noLongerWaitingFor(closed.id())));
    }
}
