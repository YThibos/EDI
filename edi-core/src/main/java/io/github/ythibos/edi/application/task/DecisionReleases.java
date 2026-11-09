package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.StageTrigger.CaptainItemAnswered;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class DecisionReleases {

    private final TaskStore tasks;
    private final TaskHolds holds;
    private final TaskStages stages;

    public DecisionReleases(TaskStore tasks, TaskHolds holds, TaskStages stages) {
        this.tasks = tasks;
        this.holds = holds;
        this.stages = stages;
    }

    public Task released(Task task, Actor actor, Instant at) {
        Task released = task.decisionAnswered();
        tasks.update(released);
        return released.closesOnItsAnswer() ? stages.moved(released, new CaptainItemAnswered(holds.of(released)), actor, at) : released;
    }
}
