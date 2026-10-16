package io.github.ythibos.edi.application.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime.PaneReading;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class PeekWorker {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final TransactionTemplate transaction;

    public PeekWorker(TaskStore tasks, WorkerStore workers, WorkerRuntime runtime, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.runtime = runtime;
        this.transaction = transaction;
    }

    public Peek peek(TaskReference reference) {
        Worker worker = transaction.execute(_ -> WorkerLookup.servingTask(workers, tasks.get(reference).id()));
        return new Peek(worker, runtime.peek(worker.window()));
    }

    public record Peek(Worker worker, PaneReading pane) {
    }
}
