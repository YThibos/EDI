package io.github.ythibos.edi.application.primary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class PrimaryWaitQuery {

    private final PrimarySessions roles;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public PrimaryWaitQuery(PrimarySessions roles, TaskStore tasks, TransactionTemplate transaction) {
        this.roles = roles;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public PrimaryWait current() {
        return transaction.execute(_ -> PrimaryWait.of(roles.role().holder(), tasks.inState(TaskState.IN_FLIGHT).size()));
    }
}
