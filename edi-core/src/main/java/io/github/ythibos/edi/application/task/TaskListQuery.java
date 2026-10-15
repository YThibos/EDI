package io.github.ythibos.edi.application.task;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class TaskListQuery {

    private static final List<TaskState> OPEN_WORK = List.of(TaskState.IN_FLIGHT, TaskState.QUEUED);

    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public TaskListQuery(TaskStore tasks, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public TaskList openWork() {
        return inStates(OPEN_WORK);
    }

    public TaskList inState(TaskState state) {
        return inStates(List.of(state));
    }

    private TaskList inStates(List<TaskState> states) {
        return transaction.execute(_ -> new TaskList(states, states.stream().flatMap(state -> tasks.inState(state).stream()).toList()));
    }
}
