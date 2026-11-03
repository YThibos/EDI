package io.github.ythibos.edi.application.primary;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.wake.WakeStreams;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.domain.task.TaskState;

@Service
public class PrimaryWaitQuery {

    private final PrimarySessions roles;
    private final WakeStreams streams;
    private final TaskStore tasks;
    private final TransactionTemplate transaction;

    public PrimaryWaitQuery(PrimarySessions roles, WakeStreams streams, TaskStore tasks, TransactionTemplate transaction) {
        this.roles = roles;
        this.streams = streams;
        this.tasks = tasks;
        this.transaction = transaction;
    }

    public PrimaryWait current() {
        return transaction.execute(_ -> {
            Optional<PrimarySession> primary = roles.role().holder();
            return PrimaryWait.of(primary, primary.flatMap(session -> streams.liveOf(session.id())), tasks.inState(TaskState.IN_FLIGHT).size());
        });
    }
}
