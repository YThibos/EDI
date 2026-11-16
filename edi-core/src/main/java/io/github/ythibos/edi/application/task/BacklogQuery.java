package io.github.ythibos.edi.application.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class BacklogQuery {

    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public BacklogQuery(TaskStore tasks, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public Backlog backlog(boolean readyOnly) {
        return transaction.execute(_ -> new Backlog(readyOnly, tasks.inState(TaskState.QUEUED).stream()
                .map(task -> new BacklogEntry(task, tasks.waitsOf(task)))
                .filter(entry -> !readyOnly || entry.isReady())
                .toList()));
    }
}
