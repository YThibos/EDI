package io.github.ythibos.edi.application.worker;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class MirrorWorker {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final TransactionTemplate transaction;

    public MirrorWorker(TaskStore tasks, WorkerStore workers, WorkerRuntime runtime, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.runtime = runtime;
        this.transaction = transaction;
    }

    public Worker servingWorkerOf(TaskReference reference) {
        return transaction.execute(_ -> WorkerLookup.servingTask(workers, tasks.get(reference).id()));
    }

    public List<String> mirror(Worker worker) {
        return runtime.mirror(worker.window());
    }
}
