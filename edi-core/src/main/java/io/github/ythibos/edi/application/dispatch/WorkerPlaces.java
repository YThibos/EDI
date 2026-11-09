package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
public class WorkerPlaces {

    private final WorkerStore workers;
    private final TaskStore tasks;
    private final WorkerRuntime runtime;

    public WorkerPlaces(WorkerStore workers, TaskStore tasks, WorkerRuntime runtime) {
        this.workers = workers;
        this.tasks = tasks;
        this.runtime = runtime;
    }

    public void requireNoLiveWorker(TaskId task) {
        liveWorkerOf(task).ifPresent(live -> {
            throw new Refusal(task + " already has a live worker, " + live.id() + "; one worker serves a task at a time.");
        });
    }

    public Optional<Worker> liveWorkerOf(TaskId task) {
        return workers.latestOf(task).filter(worker -> !worker.isRetired() && runtime.isRunning(worker.window()));
    }

    public int occupied() {
        return running().size();
    }

    public List<Worker> running() {
        return workers.serving().stream()
                .filter(Worker::countsAgainstCapacity)
                .filter(worker -> runtime.isRunning(worker.window()))
                .toList();
    }

    public boolean hasRoomOn(ProjectEntry project) {
        OptionalInt capacity = project.workerCapacity();
        return capacity.isEmpty() || occupiedOn(project.project().name()) < capacity.getAsInt();
    }

    public int occupiedOn(ProjectName project) {
        return (int) workers.serving().stream()
                .filter(Worker::countsAgainstCapacity)
                .filter(worker -> tasks.get(worker.id().task()).intake().projects().contains(project))
                .filter(worker -> runtime.isRunning(worker.window()))
                .count();
    }
}
