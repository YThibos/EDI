package io.github.ythibos.edi.application.task;

import static java.util.function.Predicate.not;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.task.StageTrigger.CaptainItemAnswered;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class DecisionReleases {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final TaskStages stages;
    private final DependantTasks dependants;
    private final EventLog events;

    public DecisionReleases(TaskStore tasks, DecisionStore decisions, TaskStages stages, DependantTasks dependants, EventLog events) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.stages = stages;
        this.dependants = dependants;
        this.events = events;
    }

    public Task released(Task task, DecisionNumber answered, Actor actor, Instant at) {
        Task released = task.decisionAnswered(answered);
        tasks.update(released);
        return closesNow(released) ? retired(released, actor, at) : released;
    }

    public Task retiredOnceAnswered(Task released, Actor actor, Instant at) {
        return closesNow(released) && hasAnAnsweredDecision(released) ? retired(released, actor, at) : released;
    }

    private static boolean closesNow(Task released) {
        return released.closesOnItsAnswer() && !released.isHeld();
    }

    private Task retired(Task answered, Actor actor, Instant at) {
        Task retired = stages.moved(answered, new CaptainItemAnswered(answered.holds()), actor, at);
        dependants.stopWaitingFor(retired);
        events.record(new TaskDone(retired.id()), actor, at);
        return retired;
    }

    private boolean hasAnAnsweredDecision(Task released) {
        return decisions.everyOneOfTask(released.id()).stream().anyMatch(not(Decision::isOpen));
    }
}
