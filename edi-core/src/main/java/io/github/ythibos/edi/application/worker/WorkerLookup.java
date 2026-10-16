package io.github.ythibos.edi.application.worker;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

final class WorkerLookup {

    private WorkerLookup() {
    }

    static Worker serving(WorkerStore workers, WorkerId id) {
        Worker worker = workers.get(id).orElseThrow(() -> new Refusal("No worker is called " + id + "."));
        if (worker.isRetired()) {
            throw new Refusal(id + " was retired; its task has another worker now.");
        }
        return worker;
    }

    static Worker servingTask(WorkerStore workers, TaskId task) {
        return workers.latestOf(task)
                .filter(worker -> !worker.isRetired())
                .orElseThrow(() -> new Refusal(task + " has no worker; dispatch it first."));
    }
}
