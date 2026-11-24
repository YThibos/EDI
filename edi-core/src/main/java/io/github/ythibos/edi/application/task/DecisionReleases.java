package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.StageTrigger.CaptainItemAnswered;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class DecisionReleases {

    private final TaskStore tasks;
    private final TaskStages stages;

    public DecisionReleases(TaskStore tasks, TaskStages stages) {
        this.tasks = tasks;
        this.stages = stages;
    }

    public Task released(Task task, DecisionNumber answered, Actor actor, Instant at) {
        Task released = task.decisionAnswered(answered);
        tasks.update(released);
        return closesNow(released) ? stages.moved(released, new CaptainItemAnswered(released.holds()), actor, at) : released;
    }

    private static boolean closesNow(Task released) {
        return released.closesOnItsAnswer() && !released.isHeld();
    }
}
