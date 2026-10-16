package io.github.ythibos.edi.application.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class RelaunchWorker {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final WorkerListQuery health;
    private final LaunchWorker launcher;
    private final TransactionTemplate transaction;

    public RelaunchWorker(TaskStore tasks, WorkerStore workers, WorkerListQuery health, LaunchWorker launcher, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.health = health;
        this.launcher = launcher;
        this.transaction = transaction;
    }

    public Launched relaunch(TaskReference reference, Actor actor) {
        Task task = transaction.execute(_ -> tasks.get(reference));
        Worker latest = transaction.execute(_ -> WorkerLookup.servingTask(workers, task.id()));
        String because = lossOf(health.judged(latest).health(), latest);
        requireNoHold(task);
        return launcher.relaunch(task.id(), LaunchKind.RESUME, null, null, because, actor);
    }

    private static String lossOf(WorkerHealth health, Worker worker) {
        return switch (health) {
            case WorkerHealth.Dead dead -> dead.cause().sentence();
            case WorkerHealth.Stopped _ -> "EDI stopped it";
            case WorkerHealth.LaunchFailed _ -> "its launch failed";
            case WorkerHealth.Finished _ -> "its session ended";
            case WorkerHealth.Starting _, WorkerHealth.Busy _, WorkerHealth.Idle _, WorkerHealth.Waiting _, WorkerHealth.LimitParked _ ->
                    throw new Refusal(worker.id() + " is still running; stop it first with edi worker stop, then relaunch it.");
        };
    }

    private static void requireNoHold(Task task) {
        if (task.isHeld()) {
            throw new Refusal(task.id() + " is held: " + task.hold().reason() + ".");
        }
    }

}
