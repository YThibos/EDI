package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.task.TaskStages;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.ChangeCause;
import io.github.ythibos.edi.domain.task.StageTrigger.Change;
import io.github.ythibos.edi.domain.task.StageTrigger.DoneReported;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.WorkerId;

@Component
public class HandoverStages {

    private final TaskStore tasks;
    private final TaskStages stages;

    public HandoverStages(TaskStore tasks, TaskStages stages) {
        this.tasks = tasks;
        this.stages = stages;
    }

    public void delivered(WorkerId worker, Actor actor, Instant at) {
        Task task = tasks.get(worker.task());
        if (task.stage().isOngoing()) {
            stages.moved(task, doneWithItsClaimsVerified(), actor, at);
        }
    }

    public void backAtWork(WorkerId worker, Actor actor, Instant at) {
        Task task = tasks.get(worker.task());
        if (task.stage().isHandedOver()) {
            stages.moved(task, new Change(ChangeCause.WORKER_WORKING), actor, at);
        }
    }

    private static DoneReported doneWithItsClaimsVerified() {
        // TODO EDI-86 the guard reads the copy's paths, the inbox and the running pipeline run itself instead of the claims DoneClaims verified
        return new DoneReported(List.of(), List.of(), List.of(), false, List.of(), true);
    }
}
