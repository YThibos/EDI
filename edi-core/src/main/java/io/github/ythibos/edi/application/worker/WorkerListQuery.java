package io.github.ythibos.edi.application.worker;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class WorkerListQuery {

    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final TransactionTemplate transaction;

    public WorkerListQuery(WorkerStore workers, WorkerRuntime runtime, TransactionTemplate transaction) {
        this.workers = workers;
        this.runtime = runtime;
        this.transaction = transaction;
    }

    public WorkerList serving() {
        List<Worker> serving = transaction.execute(_ -> workers.serving());
        return new WorkerList(serving.stream().map(worker -> new WorkerList.Entry(worker, runtime.isRunning(worker.window()))).toList());
    }
}
