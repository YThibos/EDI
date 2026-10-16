package io.github.ythibos.edi.application.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class TaskDetailQuery {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final TransactionTemplate transaction;

    public TaskDetailQuery(TaskStore tasks, DecisionStore decisions, EventLog events, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.transaction = transaction;
    }

    public TaskDetail detail(TaskReference reference) {
        return transaction.execute(_ -> {
            Task task = tasks.get(reference);
            return new TaskDetail(
                    task,
                    tasks.blockersOf(task),
                    decisions.ofTask(task.id()).orElse(null),
                    events.timelineOf(task.id()));
        });
    }
}
