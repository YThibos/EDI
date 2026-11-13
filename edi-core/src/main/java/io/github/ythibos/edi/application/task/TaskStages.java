package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.supervision.LeaveTheOutageList;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.StageChanged;
import io.github.ythibos.edi.domain.task.StageChange;
import io.github.ythibos.edi.domain.task.StageTrigger;
import io.github.ythibos.edi.domain.task.Task;

@Component
public class TaskStages {

    private final TaskStore tasks;
    private final EventLog events;
    private final LeaveTheOutageList outageList;

    public TaskStages(TaskStore tasks, EventLog events, LeaveTheOutageList outageList) {
        this.tasks = tasks;
        this.events = events;
        this.outageList = outageList;
    }

    public Task moved(Task task, StageTrigger trigger, Actor actor, Instant at) {
        return changed(task, task.transitions().move(task.stage(), trigger), actor, at);
    }

    public Task changed(Task task, StageChange change, Actor actor, Instant at) {
        Task moved = task.movedBy(change, at);
        tasks.update(moved);
        events.record(new StageChanged(change), actor, at);
        return outageList.onceNothingIsLeftToResume(moved, actor, at);
    }
}
