package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class WorkerPlaces {

    private final WorkerStore workers;
    private final TaskStore tasks;
    private final PipelineRunStore pipelineRuns;
    private final WorkerRuntime runtime;
    private final ProcessTable processes;

    public WorkerPlaces(WorkerStore workers, TaskStore tasks, PipelineRunStore pipelineRuns, WorkerRuntime runtime, ProcessTable processes) {
        this.workers = workers;
        this.tasks = tasks;
        this.pipelineRuns = pipelineRuns;
        this.runtime = runtime;
        this.processes = processes;
    }

    public void requireNoLiveWorker(TaskId task) {
        liveWorkerOf(task).ifPresent(live -> {
            throw new Refusal(Rule.ONE_WORKER_PER_TASK, task + " already has a live worker, " + live.id() + "; one worker serves a task at a time.");
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

    public ProjectRoom roomOn(ProjectEntry project) {
        ProjectName name = project.project().name();
        return new ProjectRoom(name, workingOn(name), project.workerCapacity());
    }

    private List<WorkerId> workingOn(ProjectName project) {
        return workers.serving().stream()
                .filter(Worker::countsAgainstCapacity)
                .filter(worker -> tasks.get(worker.id().task()).intake().projects().contains(project))
                .filter(worker -> runtime.isRunning(worker.window()))
                .filter(this::isWorking)
                .map(Worker::id)
                .toList();
    }

    private boolean isWorking(Worker worker) {
        return !worker.waitsOnItsQuestion() && (worker.isBusy() || hasItsPipelineRunning(worker) || runsAHeavyRun(worker));
    }

    private boolean hasItsPipelineRunning(Worker worker) {
        return pipelineRuns.latestOf(worker.id().task()).filter(PipelineRun::isRunning).isPresent();
    }

    private boolean runsAHeavyRun(Worker worker) {
        return processes.heavyRunIn(worker.window()).isPresent();
    }
}
